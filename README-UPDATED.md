# MediStock — updated project

## What changed
- Reworked the existing dashboard into a premium dark-green sidebar layout with responsive mobile navigation.
- Preserved the existing inventory, purchases, sales/billing, suppliers and alerts screens and their API-backed workflows.
- Updated the sign-in screen to match the new visual design.
- Reduced noisy SQL/bind-parameter logging and removed duplicate JPA configuration.
- Kept the existing Spring Boot + MySQL backend and schema migration behavior.

## Run on Windows
1. Extract this ZIP.
2. Open the inner `medistock` folder — the one containing `pom.xml` — in VS Code.
3. Ensure the MySQL service is running and the `medistock_user` account has access to `medistock_db`. The datasource settings are in `src/main/resources/application.properties`.
4. Open a PowerShell terminal in the folder containing `pom.xml`.
5. Run `mvn spring-boot:run`.
6. Wait for `Started MediStockApplication` in the terminal, then open `http://localhost:8080`.
7. If port 8080 is already occupied, stop the previous running app with Ctrl+C in its terminal before starting again.

## Login
Use the administrator account configured by `DataInitializer.java`. In the project version supplied for this update, the existing initializer logs its default local administrator credentials when it creates the account. If the account was created earlier, use the password you previously set; the initializer does not reset an existing user's password.

## Notes
- Existing data is stored in MySQL; do not delete the database to apply the UI update.
- `spring.jpa.hibernate.ddl-auto=update` is retained to let Hibernate update the schema on startup. For a production deployment, use database migrations and environment-based credentials instead.
- External Google Fonts and Bootstrap CDN usage requires internet access. Core page layout and custom styling are included in the HTML.
- Build verification: Maven is not available in the current editing environment, so run `mvn package` or `mvn spring-boot:run` on your Windows machine to confirm against your local Java/Maven/MySQL setup.

## Search fix
- The top search bar was a static placeholder and was hidden on the Dashboard by a CSS rule. It is now a working input (medicines, suppliers, bills); Ctrl+K focuses it.
- Backend: added GET /api/medicines/search?q= (name/category, case-insensitive).

## Live-data update (every dashboard element is now real)
New/changed backend
- `GET /api/dashboard` – 7-day sales trend, category split (lots per category, top 4 + Others, percentages total 100), latest 8 activity rows.
- `GET /api/activity` – latest 100 actions (the "View all" toggle on Recent Activity).
- `GET /api/reports/summary?from=&to=` – revenue, bills, units, average bill, purchase spend, daily sales, top medicines, spend per supplier, stock by category, stock health.
- `GET/PUT /api/settings` (PUT is admin-only), `GET /api/settings/system`.
- `GET/PUT /api/me`, `POST /api/me/password` – the signed-in user's profile and password.
- `GET /api/sales/search?q=` – bill search beyond the latest 100.
- New tables created automatically by Hibernate (`ddl-auto=update`): `activity_log`, `app_settings`; new nullable columns `users.full_name`, `users.email`, `medicines.created_at`. No data is lost.
- On the first start, existing sales and purchases are copied into `activity_log` so Recent Activity is not empty (who made them is unknown, shown as "—").
- Alert thresholds now live in the database (Settings screen). `application.properties` values are only the first-run defaults.

Security / correctness fixes found while checking
- `/api/medicines/**` was open to anyone without logging in (including add and delete). Every API call now requires login; an expired session returns 401 and the page sends you to the sign-in screen.
- `POST /api/medicines` now validates input and can no longer overwrite an existing lot by sending an `id`.
- Dates in the browser used the UTC date (`toISOString`), which is the previous day in India between 00:00 and 05:30. Now local dates.

Dates and time zone: the server uses the computer's time zone for "today" and for the 7-day graph. Run it on a machine set to India time (the Settings > System panel shows the zone in use).
