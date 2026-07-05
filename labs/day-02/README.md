# Day 2 — Run the course stack with Docker Compose

> Starting point: this repository. No app build — you practise on the **real course stack**
> (`infra/compose.yaml`). No `starter/` needed.

## Objective

Bring up the course's infrastructure (PostgreSQL + Kafka + Kafka UI) with one command, inspect it,
step *inside* a running container, and prove that a named volume keeps data alive across a container
restart. By the end you can do the one thing the whole second week depends on: get a shell inside a
running container.

## Concepts in play

- [The problem containers solve](../../docs/content/day-02/index.md#the-problem-containers-solve)
- [Images, containers, and layers](../../docs/content/day-02/index.md#images-containers-and-layers)
- [Containers are ephemeral; volumes are not](../../docs/content/day-02/index.md#containers-are-ephemeral-volumes-are-not)
- [`docker exec` — step inside a running container](../../docs/content/day-02/index.md#docker-exec-step-inside-a-running-container)
- [Compose: a whole system as one file](../../docs/content/day-02/index.md#compose-a-whole-system-as-one-file)
- [Healthy, not just started](../../docs/content/day-02/index.md#healthy-not-just-started)
- [Host versus network addresses](../../docs/content/day-02/index.md#host-versus-network-addresses)

## Steps

1. **Confirm Docker is alive.** Run `docker version` (you should see both a Client *and* a Server)
   and `docker run --rm hello-world`. If the daemon is not running, fix that before going further.
2. **See an image is just layers.** `docker pull postgres:16`, then `docker images` (it's now
   local) and `docker history postgres:16` (the stack of cached layers).
3. **Bring up the stack.** From the repo root, `task up`. Then `docker compose -f infra/compose.yaml ps`
   and confirm all three services are `Up` and `postgres` is `healthy`. (If you already run a local
   Postgres on 5432, `export POSTGRES_HOST_PORT=5433` first, then `task up`.)
4. **Read the logs.** `docker compose -f infra/compose.yaml logs -f postgres` and watch the startup;
   `Ctrl-C` to stop following. Open Kafka UI in a browser at <http://localhost:8081>.
5. **Step inside the database** — the Day 6 move. Open a SQL shell *inside* the container and create
   a table:
   ```bash
   docker compose -f infra/compose.yaml exec postgres psql -U orders -d orders
   -- inside psql:
   CREATE TABLE persistence_demo (note text);
   INSERT INTO persistence_demo VALUES ('survived the restart');
   \dt
   \q
   ```
6. **Step inside Kafka** — the Day 7 move. List topics using the broker's own CLI, from inside its
   container:
   ```bash
   docker compose -f infra/compose.yaml exec kafka \
     /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   ```
7. **Prove the volume persists.** Stop the stack *without* deleting volumes, bring it back, and check
   your table is still there:
   ```bash
   docker compose -f infra/compose.yaml down        # NOTE: no -v — keeps the pgdata volume
   task up
   docker compose -f infra/compose.yaml exec postgres psql -U orders -d orders -c 'SELECT * FROM persistence_demo;'
   ```
   The row should still be there.
8. **Prove `-v` wipes it.** Now reset the world and confirm the data is gone:
   ```bash
   task down                                         # task down includes -v — deletes pgdata
   task up
   docker compose -f infra/compose.yaml exec postgres psql -U orders -d orders -c '\dt'
   ```
   The `persistence_demo` table is gone — that is what `-v` does.
9. **Map the file to what you saw.** Open `infra/compose.yaml` and find, for each of the three
   services, its image and tag, its published ports, the `pgdata` volume, and the `postgres`
   healthcheck. Be able to say *why* a service inside the network uses `postgres:5432` while your
   laptop uses `localhost:5432`.

## Acceptance criteria

- [ ] `docker compose -f infra/compose.yaml ps` shows Postgres, Kafka, and Kafka UI `Up`, with
      `postgres` reported `healthy`.
- [ ] You opened an interactive shell (or `psql`) *inside* a running container with
      `docker compose exec`.
- [ ] A row written to Postgres **survived** a `docker compose down` (no `-v`) followed by `task up`.
- [ ] After `task down` (which uses `-v`) and `task up`, that data is **gone** — and you can explain why.
- [ ] You can point at each service in `infra/compose.yaml` and name its image, ports, volume, and
      (for Postgres) its healthcheck.

## Stretch goals

- Override the host port: `export POSTGRES_HOST_PORT=5433`, restart the stack, and confirm with
  `docker compose -f infra/compose.yaml ps` that Postgres is now published on 5433 — while
  `postgres:5432` is unchanged inside the network.
- Explore Kafka UI: create a topic in the browser, then confirm it appears via the
  `kafka-topics.sh --list` command from step 6.
- Inspect layers: run `docker history postgres:16` and identify the base layer versus the layers
  Postgres adds on top.
- Read the real multi-stage build: open `apps/order-service/Dockerfile` and identify the two
  `FROM` stages, the `COPY --from=build` bridge, and the `USER app` line. You'll build it for real
  on Day 8.

> Tidy up when you're done for the day with `task down` (this removes the volumes). Leave the stack
> running if you want to keep your data between sessions.
