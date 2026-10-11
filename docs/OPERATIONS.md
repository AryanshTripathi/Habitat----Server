# Operations

Links to the services that run Habitat.

| Service | What it's for | Link |
|---|---|---|
| Render | Hosts the Spring service: deploys, logs, environment variables | https://dashboard.render.com/web/srv-db2glivlot8c73f4p6vg/deploys |
| Neon | Postgres database: tables, SQL editor, branches | https://console.neon.tech/app/org-shy-band-90925464/projects |
| cron-job.org | Scheduled pings to the server (limits Render cold starts) | https://console.cron-job.org/jobs |
| Live API | Deployed base URL used by the app | https://habitat-server.onrender.com |
| Client repo | Expo / React Native app | https://github.com/AryanshTripathi/Habitat----Client |

## Notes

- Render's free tier sleeps after a period of inactivity, so the first request can take up to a minute or two. The client allows for this.
- The cron job exists to keep the service warm. If the app feels slow to start, check that the job is enabled and succeeding.
