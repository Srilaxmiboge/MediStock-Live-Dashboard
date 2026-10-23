package com.medistock.service;

import com.medistock.dto.InsightDtos.*;
import com.medistock.model.ActivityLog;
import com.medistock.model.Medicine;
import com.medistock.model.Sale;
import com.medistock.model.User;
import com.medistock.repository.ActivityLogRepository;
import com.medistock.repository.MedicineRepository;
import com.medistock.repository.SaleRepository;
import com.medistock.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Real numbers behind the dashboard graph, category donut and recent-activity table. */
@Service
public class DashboardService {
    static final int TREND_DAYS = 7;
    private static final int MAX_CATEGORY_SLICES = 4;

    private final SaleRepository saleRepository;
    private final MedicineRepository medicineRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public DashboardService(SaleRepository saleRepository, MedicineRepository medicineRepository,
                            ActivityLogRepository activityLogRepository, UserRepository userRepository) {
        this.saleRepository = saleRepository;
        this.medicineRepository = medicineRepository;
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        List<Medicine> medicines = medicineRepository.findAll();
        return new DashboardSummary(salesTrend(), categories(medicines), medicines.size(), recentActivity(),
                LocalDateTime.now());
    }

    private List<TrendPoint> salesTrend() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1L);
        Map<LocalDate, BigDecimal> revenue = new HashMap<>();
        Map<LocalDate, Integer> bills = new HashMap<>();
        Map<LocalDate, Integer> units = new HashMap<>();

        for (Sale sale : saleRepository.findWithItemsBetween(start.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            LocalDate day = sale.getSoldAt().toLocalDate();
            revenue.merge(day, sale.getTotal(), BigDecimal::add);
            bills.merge(day, 1, Integer::sum);
            int soldUnits = sale.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
            units.merge(day, soldUnits, Integer::sum);
        }

        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            points.add(new TrendPoint(day, revenue.getOrDefault(day, BigDecimal.ZERO), bills.getOrDefault(day, 0),
                    units.getOrDefault(day, 0)));
        }
        return points;
    }

    /** Lots per category (case-insensitive), largest four plus "Others"; percentages always add up to 100. */
    List<CategorySlice> categories(List<Medicine> medicines) {
        Map<String, String> displayNames = new HashMap<>();
        Map<String, int[]> totals = new HashMap<>(); // key -> {lots, units}
        for (Medicine medicine : medicines) {
            String name = StringUtils.hasText(medicine.getCategory()) ? medicine.getCategory().trim() : "Uncategorised";
            String key = name.toLowerCase(Locale.ROOT);
            displayNames.putIfAbsent(key, name);
            int[] sums = totals.computeIfAbsent(key, k -> new int[2]);
            sums[0] += 1;
            sums[1] += Math.max(0, medicine.getQuantity());
        }

        List<Map.Entry<String, int[]>> ordered = new ArrayList<>(totals.entrySet());
        ordered.sort((a, b) -> {
            int byLots = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return byLots != 0 ? byLots : displayNames.get(a.getKey()).compareToIgnoreCase(displayNames.get(b.getKey()));
        });

        List<String> names = new ArrayList<>();
        List<Integer> lots = new ArrayList<>();
        List<Integer> units = new ArrayList<>();
        int otherLots = 0;
        int otherUnits = 0;
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<String, int[]> entry = ordered.get(i);
            boolean overflow = ordered.size() > MAX_CATEGORY_SLICES + 1 && i >= MAX_CATEGORY_SLICES;
            if (overflow) {
                otherLots += entry.getValue()[0];
                otherUnits += entry.getValue()[1];
            } else {
                names.add(displayNames.get(entry.getKey()));
                lots.add(entry.getValue()[0]);
                units.add(entry.getValue()[1]);
            }
        }
        if (otherLots > 0) {
            names.add("Others");
            lots.add(otherLots);
            units.add(otherUnits);
        }

        int[] counts = lots.stream().mapToInt(Integer::intValue).toArray();
        int[] percents = percentages(counts, medicines.size());
        List<CategorySlice> slices = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            slices.add(new CategorySlice(names.get(i), lots.get(i), units.get(i), percents[i]));
        }
        return slices;
    }

    /** Largest-remainder rounding so the displayed percentages total exactly 100. */
    static int[] percentages(int[] counts, int total) {
        int n = counts.length;
        int[] result = new int[n];
        if (total <= 0 || n == 0) {
            return result;
        }
        double[] remainder = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            double exact = counts[i] * 100.0 / total;
            result[i] = (int) Math.floor(exact);
            remainder[i] = exact - result[i];
            assigned += result[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(remainder[b], remainder[a]));
        for (int k = 0; k < 100 - assigned && k < n; k++) {
            result[order[k]]++;
        }
        return result;
    }

    private List<ActivityView> recentActivity() {
        return toViews(activityLogRepository.findTop8ByOrderByOccurredAtDescIdDesc());
    }

    /** The latest 100 actions, for the "View all" toggle on Recent Activity. */
    @Transactional(readOnly = true)
    public List<ActivityView> getActivity() {
        return toViews(activityLogRepository.findTop100ByOrderByOccurredAtDescIdDesc());
    }

    private List<ActivityView> toViews(List<ActivityLog> rows) {
        Map<String, String> names = new HashMap<>();
        List<ActivityView> views = new ArrayList<>();
        for (ActivityLog row : rows) {
            String user = null;
            if (row.getUsername() != null) {
                user = names.computeIfAbsent(row.getUsername(), username -> userRepository.findByUsername(username)
                        .map(AccountService::displayName).orElse(username));
            }
            views.add(new ActivityView(row.getId(), row.getType(), row.getDetails(), row.getAmount(), user,
                    row.getOccurredAt()));
        }
        return views;
    }
}
