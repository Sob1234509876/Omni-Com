package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.auth.AuthResult;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.auth.PasswordHasher;
import io.github.sob1234509876.oc.server.auth.Pbkdf2PasswordHasher;
import io.github.sob1234509876.oc.server.auth.UserRecord;
import io.github.sob1234509876.oc.server.auth.UserStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self check of the authentication package: accounts, password hashing and the local text store.
 *
 * <p>The check owns its own scratch directory under the system temporary directory and removes it
 * afterwards, so it never touches a real server's user file. It is a plain program rather than a test
 * framework suite, matching the other modules of the repository; run it with
 * {@code ./gradlew :projects:oc:oc-game-server:selfcheck}.
 *
 * <p>Most assertions run against {@link MinimalHasher} instead of {@link Pbkdf2PasswordHasher}, because
 * the production hasher is deliberately slow and a check that pays for hundreds of thousands of PBKDF2
 * iterations per account would take minutes. The production hasher itself is checked separately, with a
 * single call, for the properties that do not depend on the iteration count.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class AuthCheck {

    /** A name no other check uses, so one scratch directory can host the whole check. */
    private static final String NAME = "Player_01";

    /** A name that passes every rule, used where only the registration result matters. */
    private static final String SECOND_NAME = "player.two";

    /** A password that is long enough for every check. */
    private static final String PASSWORD = "correct horse battery";

    /** The iteration count the check hasher reports; the lowest a {@link UserRecord} accepts. */
    private static final int ITERATIONS = 10_000;

    private AuthCheck() {
    }

    public static void run(Check check) {
        Path root = createScratchDirectory();
        try {
            Path userFile = root.resolve("users.txt");
            UserStore store = LocalUserStore.at(userFile);
            AuthService service = new AuthService(store, new MinimalHasher());
            registrationAndLogin(check, service, userFile);
            storedState(check, service, store);
            persistence(check, userFile);
            concurrency(check, root);
            escaping(check, root);
            corruptedFile(check, root);
            hashing(check);
        } finally {
            deleteRecursively(root);
        }
    }

    private static void registrationAndLogin(Check check, AuthService service, Path userFile) {
        check.section("1) 注册与登录");
        check.equal(AuthResult.SUCCESS, service.register(NAME, PASSWORD), "注册新账号成功");
        check.equal(AuthResult.SUCCESS, service.authenticate(NAME, PASSWORD), "用正确密码登录成功");
        check.that(service.authenticate(NAME, PASSWORD).ok(), "成功结果的 ok() 为真");
        check.equal(AuthResult.WRONG_PASSWORD, service.authenticate(NAME, "wrong password entirely"),
                "密码错误被拒绝");
        check.equal(AuthResult.UNKNOWN_USER, service.authenticate("Nobody_Here", PASSWORD), "不存在的账号被识别");
        check.equal(AuthResult.ALREADY_REGISTERED, service.register(NAME, PASSWORD), "重复注册被拒绝");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.register("shortpw", "12345"), "过短的密码被拒绝");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.register("Bad Name", PASSWORD), "含空格的用户名被拒");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.register("ab", PASSWORD), "过短的用户名被拒绝");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.register(NAME, null), "空密码被拒绝");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.authenticate(NAME, null), "空密码登录被拒绝");
        check.equal(AuthResult.INVALID_CREDENTIALS, service.authenticate(null, PASSWORD), "空用户名登录被拒绝");
        check.that(Files.exists(userFile), "注册后用户文件已生成");
    }

    private static void storedState(Check check, AuthService service, UserStore store) {
        check.section("2) 记录状态");
        check.equal(1, service.userCount(), "被拒绝的注册没有留下账号");
        Optional<UserRecord> found = service.find(NAME);
        check.that(found.isPresent(), "可以查到已注册的账号");
        check.that(found.orElseThrow().lastLoginMillis() > 0L, "第一次登录已经写下时间戳");
        check.that(found.orElseThrow().lastLoginMillis() <= System.currentTimeMillis(),
                "登录时间不在未来");

        long before = found.orElseThrow().lastLoginMillis();
        // A sentinel time proves the refresh: comparing two clock readings taken a millisecond apart
        // would pass even when nothing is written, because the stored timestamp would just be stale.
        store.save(found.orElseThrow().withLastLogin(1L));
        check.equal(1L, service.find(NAME).orElseThrow().lastLoginMillis(), "可以改写登录时间以验证刷新");
        check.that(service.authenticate(NAME, PASSWORD).ok(), "再次登录成功");
        UserRecord afterLogin = service.find(NAME).orElseThrow();
        check.that(afterLogin.lastLoginMillis() > 1L, "authenticate 刷新了 lastLoginMillis");
        check.that(afterLogin.lastLoginMillis() >= before, "刷新后的时间不早于上一次登录");
        check.that(afterLogin.registeredAtMillis() >= 0L, "注册时间不为负");
        check.equal(ITERATIONS, afterLogin.passwordIterations(), "记录保存了哈希迭代次数");
        check.equal(found.orElseThrow().registeredAtMillis(), afterLogin.registeredAtMillis(),
                "登录不会改写注册时间");
        check.equal(found.orElseThrow().passwordHash(), afterLogin.passwordHash(), "登录不会改写密码哈希");
    }

    private static void persistence(Check check, Path userFile) {
        check.section("3) 持久化与原子写入");
        UserStore reopened = LocalUserStore.at(userFile);
        check.equal(AuthResult.SUCCESS, new AuthService(reopened, new MinimalHasher())
                .authenticate(NAME, PASSWORD), "新存储实例能读到已有账号");
        check.that(reopened.find(NAME).orElseThrow().lastLoginMillis() > 0L, "登录时间落到了磁盘上");
        check.that(!Files.exists(userFile.resolveSibling(userFile.getFileName() + ".tmp")),
                "原子写入后没有残留 .tmp 文件");
        check.that(firstLine(userFile).startsWith("#"), "文件首行是加载器会跳过的注释");

        Path missingDirectory = userFile.getParent().resolve("nested").resolve("deeper");
        LocalUserStore created = LocalUserStore.inDirectory(missingDirectory);
        check.that(!Files.exists(missingDirectory), "目标目录一开始不存在");
        created.save(sample("Dir_Check"));
        created.flush();
        check.that(Files.exists(missingDirectory.resolve(LocalUserStore.FILE_NAME)),
                "flush 创建了缺失的目录和文件");
        check.that(created.persistent(), "本地存储报告自己是持久化的");
        check.equal(missingDirectory.resolve(LocalUserStore.FILE_NAME), created.location(),
                "location() 指向被写入的文件");
    }

    private static void concurrency(Check check, Path root) {
        check.section("4) 并发");
        LocalUserStore store = LocalUserStore.at(root.resolve("concurrent.txt"));
        AuthService service = new AuthService(store, new MinimalHasher());
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        Thread[] workers = new Thread[threads];
        for (int index = 0; index < threads; index++) {
            String name = "Concurrent_" + index;
            workers[index] = new Thread(() -> {
                try {
                    start.await();
                    if (service.register(name, PASSWORD).ok()) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add(e);
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            }, "auth-check-" + index);
            workers[index].start();
        }
        start.countDown();
        for (Thread worker : workers) {
            join(worker, failures);
        }
        check.equal(0, failures.size(), "并发注册没有抛出异常");
        check.equal(threads, successes.get(), "并发注册全部成功");
        check.equal(threads, service.userCount(), "并发注册的账号一个都没丢");
    }

    /**
     * Waits for one concurrent registration thread.
     *
     * <p>Separated only to keep the assertion block readable. A thread that never finishes would hang
     * the check, so a bounded wait turns a deadlock into a reported failure instead of a stuck build.
     */
    private static void join(Thread worker, List<Throwable> failures) {
        try {
            worker.join(TimeUnit.SECONDS.toMillis(30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(e);
        }
    }

    /**
     * The line format survives the characters that could break it.
     *
     * <p>Half of this cannot be reached through {@link AuthService}: {@link UserRecord} rejects a name
     * outside the account name rules, so a name containing {@code %} never gets here. That is exactly
     * why it is tested at the store level. Every line is split on tabs, so a separator inside a field
     * would shift the remaining fields and corrupt the account rather than fail loudly, and the
     * escaping is what makes that impossible by construction instead of by hoping the name rules stay
     * narrow. {@link UserStore} is a public interface with a documented shape, so a store must not
     * depend on its caller's validation to keep the file parseable.
     */
    private static void escaping(Check check, Path root) {
        check.section("5) 转义与文本格式");
        Path file = root.resolve("escaped.txt");
        LocalUserStore store = LocalUserStore.at(file);

        UserRecord realistic = new UserRecord("Odd_Name.9-9", "ab%12", "cd\t34", ITERATIONS, 5L, 6L);
        store.save(realistic);
        store.flush();
        check.equal(realistic, LocalUserStore.at(file).find("Odd_Name.9-9").orElseThrow(),
                "合法记录原样往返（哈希里的 % 不影响读取）");

        UserRecord hostile = new UserRecord("Hostile_Name", "h\t1", "s\r2\n3", ITERATIONS, 7L, 8L);
        store.save(hostile);
        store.flush();
        check.equal(2, LocalUserStore.at(file).all().size(), "含制表符与换行的记录没有拆成多行");
        check.that(LocalUserStore.at(file).all().contains(hostile), "含制表符与换行的字段原样往返");
        check.that(store.all().iterator().next().equals(realistic), "all() 保持插入顺序");

        check.that(!store.remove("Never_Saved"), "删除不存在的键返回 false");
        check.that(store.remove("Hostile_Name"), "删除已存在的键返回 true");
        store.flush();
        check.that(LocalUserStore.at(file).find("Hostile_Name").isEmpty(), "删除被写进文件");
        check.that(LocalUserStore.at(file).find("Odd_Name.9-9").isPresent(), "删除没有误伤别的记录");
        check.that(LocalUserStore.at(file).find(null).isEmpty(), "查空名字返回空");

        Collection<UserRecord> snapshot = LocalUserStore.at(file).all();
        boolean immutable;
        try {
            snapshot.add(realistic);
            immutable = false;
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        check.that(immutable, "all() 的返回值不可变");
    }

    private static void corruptedFile(Check check, Path root) {
        check.section("6) 损坏的文件");
        Path broken = root.resolve("broken.txt");
        write(broken, "# header\nPlayer_01\thash\tsalt\t10000\t0\t0\nthis line has no tabs\n");
        try {
            LocalUserStore.at(broken);
            check.that(false, "损坏的行被拒绝");
        } catch (IllegalStateException e) {
            check.that(e.getMessage().contains("line 3"), "错误信息指出行号 3");
        }
        Path missing = root.resolve("absent.txt");
        check.that(LocalUserStore.at(missing).all().isEmpty(), "文件不存在时是空存储");
    }

    private static void hashing(Check check) {
        check.section("7) 密码哈希");
        MinimalHasher hasher = new MinimalHasher();
        String salt = hasher.newSalt();
        check.that(salt.equals(hasher.newSalt()), "检查用的哈希器盐是确定的");
        String expected = hasher.hash(PASSWORD, salt, ITERATIONS);
        check.equal(expected, hasher.hash(PASSWORD, salt, ITERATIONS), "相同输入得到相同哈希");
        check.that(!expected.equals(hasher.hash(PASSWORD, "other-salt", ITERATIONS)), "不同盐得到不同哈希");
        check.that(hasher.verify(PASSWORD, salt, ITERATIONS, expected), "verify 接受正确密码");
        check.that(!hasher.verify("another password", salt, ITERATIONS, expected), "verify 拒绝错误密码");
        check.that(!hasher.verify(PASSWORD, salt, ITERATIONS, null), "verify 拒绝空哈希");
        check.equal(ITERATIONS, hasher.defaultIterations(), "检查用的哈希器使用最小迭代次数");

        Pbkdf2PasswordHasher production = new Pbkdf2PasswordHasher();
        check.equal(Pbkdf2PasswordHasher.DEFAULT_ITERATIONS, production.defaultIterations(),
                "生产哈希器默认迭代次数是 210000");
        String productionSalt = production.newSalt();
        check.equal(16, productionSalt.length() / 2, "盐是 16 字节");
        String productionHash = production.hash(PASSWORD, productionSalt, ITERATIONS);
        check.that(productionHash.length() == 64 && productionHash.matches("[0-9a-f]{64}"),
                "PBKDF2 输出是 32 字节的十六进制");
        check.that(production.verify(PASSWORD, productionSalt, ITERATIONS, productionHash),
                "生产哈希器能验证自己产生的哈希");
        check.that(!production.verify("other password", productionSalt, ITERATIONS, productionHash),
                "生产哈希器拒绝错误密码");
        check.that(!production.newSalt().equals(productionSalt), "每次注册得到新的随机盐");

        salting(check);
    }

    /**
     * Two accounts with the same password must not share a hash.
     *
     * <p>This is the one property that must be shown with the production hasher end to end, because it
     * is about the salt and not about the iteration count: the check hasher has a fixed salt, so it can
     * only prove that different salts produce different hashes, never that the service actually picks
     * different ones.
     */
    private static void salting(Check check) {
        Path directory = createScratchDirectory();
        try {
            LocalUserStore store = LocalUserStore.inDirectory(directory);
            AuthService service = new AuthService(store, new Pbkdf2PasswordHasher());
            check.equal(AuthResult.SUCCESS, service.register("Salt_One", PASSWORD), "第一个账号注册成功");
            check.equal(AuthResult.SUCCESS, service.register("Salt_Two", PASSWORD), "第二个账号注册成功");
            UserRecord first = service.find("Salt_One").orElseThrow();
            UserRecord second = service.find("Salt_Two").orElseThrow();
            check.that(!first.passwordSalt().equals(second.passwordSalt()), "两个账号的盐不同");
            check.that(!first.passwordHash().equals(second.passwordHash()), "相同密码产生不同哈希");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static UserRecord sample(String name) {
        return new UserRecord(name, "hash", "salt", ITERATIONS, 1L, 0L);
    }

    private static Path createScratchDirectory() {
        try {
            return Files.createTempDirectory("oc-game-server-auth-check");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String firstLine(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(AuthCheck::delete);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A deliberately cheap {@link PasswordHasher} for the check.
     *
     * <p>It hashes with SHA-256 and a fixed salt. That is completely unfit for production and is not
     * meant to be: it exists so the check can run a hundred authentications instantly. The production
     * parameters are asserted separately on {@link Pbkdf2PasswordHasher}.
     */
    private static final class MinimalHasher implements PasswordHasher {

        private static final String SALT = "00000000000000000000000000000000";

        @Override
        public String newSalt() {
            return SALT;
        }

        @Override
        public String hash(String password, String salt, int iterations) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int defaultIterations() {
            return ITERATIONS;
        }
    }
}
