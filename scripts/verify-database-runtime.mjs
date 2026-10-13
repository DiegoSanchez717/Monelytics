import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";
import { promisify } from "node:util";

// This probe owns one disposable container and its anonymous volume. It never
// mounts host paths, publishes ports, reads .env, or targets Compose resources.
// Usage: node scripts/verify-database-runtime.mjs [monelytics-database:latest]
const execute = promisify(execFile);
const image = process.argv[2] ?? "monelytics-database:latest";
const owner = randomUUID();
const containerName = `monelytics-db-runtime-${owner}`;
const ownershipLabel = "dev.monelytics.runtime-verification";
const checks = {};
let containerId;
let allocationStarted = false;

async function docker(args, expectedExitCode = 0, timeout = 30000) {
  try {
    const result = await execute("docker", args, {
      encoding: "utf8",
      timeout,
      windowsHide: true,
      maxBuffer: 2 * 1024 * 1024,
    });
    assert.equal(
      expectedExitCode,
      0,
      `docker ${args[0]} unexpectedly succeeded`,
    );
    return result.stdout.trim();
  } catch (error) {
    if (Number.isInteger(error.code) && error.code === expectedExitCode) {
      return `${error.stdout ?? ""}\n${error.stderr ?? ""}`.trim();
    }
    // Avoid echoing complete CLI arguments, including the synthetic password.
    const detail =
      error.stderr?.trim() ||
      (error.killed
        ? "command timed out"
        : `exit code ${error.code ?? "unknown"}`);
    throw new Error(`docker ${args[0]} failed: ${detail}`, { cause: error });
  }
}

async function inspectContainer(reference = containerId) {
  return JSON.parse(await docker(["inspect", reference]))[0];
}

async function dockerLogs() {
  const result = await execute("docker", ["logs", containerId], {
    encoding: "utf8",
    timeout: 10000,
    windowsHide: true,
    maxBuffer: 2 * 1024 * 1024,
  });
  // PostgreSQL logs to stderr while the official initialization script uses stdout.
  return `${result.stdout}\n${result.stderr}`;
}

async function waitForHealthy(requireInitialization = false) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    const container = await inspectContainer();
    assert.equal(
      container.State.Running,
      true,
      "Disposable database exited before readiness",
    );
    if (container.State.Health?.Status === "healthy") {
      const logs = await dockerLogs();
      // pg_isready can succeed against the temporary initialization server.
      // Wait for initdb to finish before querying the final PostgreSQL process.
      if (
        !requireInitialization ||
        logs.includes("PostgreSQL init process complete")
      )
        return;
    }
    await delay(750);
  }
  throw new Error(
    "Disposable database did not become healthy within 60 seconds",
  );
}

async function sql(statement) {
  return docker([
    "exec",
    containerId,
    "psql",
    "-U",
    "runtimecheck",
    "-d",
    "runtimecheck",
    "--set=ON_ERROR_STOP=1",
    "--tuples-only",
    "--no-align",
    "-c",
    statement,
  ]);
}

async function stopGracefully() {
  await docker(["stop", "--time", "30", containerId], 0, 40000);
  const container = await inspectContainer();
  assert.equal(container.State.ExitCode, 0, "PostgreSQL did not exit cleanly");
  assert.equal(
    container.State.OOMKilled,
    false,
    "PostgreSQL was killed by memory pressure",
  );
  const logs = await dockerLogs();
  assert.match(logs, /received fast shutdown request/);
  assert.match(logs, /database system is shut down/);
}

async function cleanupOwnedContainer() {
  if (!allocationStarted) return;
  let container;
  try {
    container = await inspectContainer(containerId ?? containerName);
  } catch (error) {
    if (/No such (object|container)/i.test(error.message)) return;
    throw error;
  }
  assert.equal(
    container.Config.Labels?.[ownershipLabel],
    owner,
    "Refusing to remove a container without this probe's ownership label",
  );
  assert.equal(
    container.Name,
    `/${containerName}`,
    "Refusing to remove a container with an unexpected name",
  );
  if (containerId) assert.equal(container.Id, containerId);
  assert.ok(
    container.Mounts.every((mount) => mount.Type === "volume"),
    "Refusing cleanup: the probe must never contain host bind mounts",
  );
  // --volumes removes only anonymous volumes attached to this exact container.
  await docker(["rm", "--force", "--volumes", container.Id]);
}

async function verify() {
  assert.ok(process.argv.length <= 3, "Pass at most one Docker image argument");
  const imageInfo = JSON.parse(await docker(["image", "inspect", image]))[0];
  assert.deepEqual(imageInfo.Config.Entrypoint, ["docker-entrypoint.sh"]);
  assert.deepEqual(imageInfo.Config.Cmd, ["postgres"]);
  assert.equal(imageInfo.Config.StopSignal, "SIGINT");
  assert.ok(
    Object.hasOwn(imageInfo.Config.Volumes, "/var/lib/postgresql/data"),
  );
  checks.officialRuntimeContract = "passed";

  allocationStarted = true;
  containerId = await docker([
    "create",
    "--name",
    containerName,
    "--label",
    `${ownershipLabel}=${owner}`,
    "--network",
    "none",
    "--health-cmd",
    "pg_isready -U runtimecheck -d runtimecheck",
    "--health-interval",
    "1s",
    "--health-timeout",
    "2s",
    "--health-retries",
    "60",
    "--env",
    "POSTGRES_USER=runtimecheck",
    "--env",
    "POSTGRES_DB=runtimecheck",
    "--env",
    "POSTGRES_PASSWORD=DisposableRuntimeOnly!2026",
    "--env",
    "POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256",
    image,
  ]);
  assert.match(containerId, /^[0-9a-f]{64}$/);
  const initial = await inspectContainer();
  assert.equal(initial.HostConfig.NetworkMode, "none");
  assert.deepEqual(initial.HostConfig.PortBindings, {});
  assert.equal(initial.Mounts.length, 1);
  assert.equal(initial.Mounts[0].Type, "volume");
  assert.equal(initial.Mounts[0].Destination, "/var/lib/postgresql/data");
  checks.isolatedAnonymousStorage = "passed";

  await docker(["start", containerId]);
  await waitForHealthy(true);
  assert.equal(await sql("SELECT current_user;"), "runtimecheck");
  const serverVersion = await sql("SHOW server_version;");
  assert.match(serverVersion, /^17\.11(?:\s|$)/);
  checks.freshInitializationAndHealth = "passed";

  const status = await docker(["exec", containerId, "cat", "/proc/1/status"]);
  assert.match(status, /^Name:\s+postgres$/m);
  assert.match(status, /^Uid:\s+70\s+70\s+70\s+70$/m);
  assert.match(status, /^Gid:\s+70\s+70\s+70\s+70$/m);
  assert.equal(
    await docker(["exec", containerId, "su-exec", "postgres", "id", "-u"]),
    "70",
  );
  await docker([
    "exec",
    containerId,
    "sh",
    "-c",
    'test ! -e /usr/local/bin/gosu && grep -q "exec su-exec postgres " /usr/local/bin/docker-entrypoint.sh && grep -q "exec su-exec postgres " /usr/local/bin/docker-ensure-initdb.sh',
  ]);
  checks.nativeHelperAndPostgresPidOne = "passed";

  // Both initialization helpers must still work after the gosu replacement.
  await docker(["exec", containerId, "docker-ensure-initdb.sh"]);
  const enforced = await docker(
    ["exec", containerId, "docker-enforce-initdb.sh"],
    1,
  );
  assert.match(enforced, /\(unexpected\) database found/);
  checks.initializationHelpers = "passed";

  await sql(
    "CREATE TABLE runtime_probe (id integer PRIMARY KEY, note text NOT NULL); INSERT INTO runtime_probe VALUES (1, 'persisted across restart');",
  );
  await stopGracefully();
  checks.gracefulFastShutdown = "passed";
  await docker(["start", containerId]);
  await waitForHealthy();
  assert.equal(
    await sql("SELECT note FROM runtime_probe WHERE id = 1;"),
    "persisted across restart",
  );
  const restartLogs = await dockerLogs();
  assert.match(restartLogs, /Skipping initialization/);
  checks.existingVolumeRestartAndPersistence = "passed";
  await stopGracefully();

  return { image, imageId: imageInfo.Id, serverVersion, checks };
}

let failure;
let result;
try {
  result = await verify();
} catch (error) {
  failure = error;
} finally {
  try {
    await cleanupOwnedContainer();
    checks.ownedResourcesRemoved = "passed";
  } catch (error) {
    failure ??= error;
  }
}
if (failure) {
  console.error(failure.message);
  process.exitCode = 1;
} else {
  console.log(JSON.stringify(result, null, 2));
}
