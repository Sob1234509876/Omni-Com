package io.github.sob1234509876.oc.server.auth;

/**
 * The outcome of a registration or a login attempt.
 *
 * <p>Authentication answers with a value instead of an exception or a bare boolean, because a client
 * needs to tell the cases apart to say something useful. A boolean would collapse "no such account"
 * and "wrong password" into one answer, and a caller that wants to show the difference would end up
 * re-implementing the lookup to find out which it was.
 *
 * <p>The distinction is also what keeps the messages honest: {@link #UNKNOWN_USER} and
 * {@link #WRONG_PASSWORD} are both reported as they are here, and it is the caller's decision whether a
 * public login screen should merge them into a vaguer message for an untrusted audience.
 */
public enum AuthResult {

    /** The credentials were accepted. */
    SUCCESS("登录成功。"),

    /** No account has that name. */
    UNKNOWN_USER("用户名不存在。"),

    /** The account exists, but the password does not match. */
    WRONG_PASSWORD("密码错误。"),

    /** The requested name is already taken. */
    ALREADY_REGISTERED("该用户名已被注册。"),

    /**
     * The submitted credentials are unusable: a name that does not fit the account name rules, or a
     * password that is too short.
     *
     * <p>Both cases share one result because the client fixes them the same way, by typing something
     * else, and because detailing which field was refused would tell an attacker how far a guessed name
     * got before it was rejected.
     */
    INVALID_CREDENTIALS("用户名或密码格式不正确。");

    private final String message;

    AuthResult(String message) {
        this.message = message;
    }

    /** Whether the attempt succeeded. */
    public boolean ok() {
        return this == SUCCESS;
    }

    /**
     * A Chinese sentence describing the result, ready to show to a player.
     *
     * <p>The text lives on the constant so that every client, and the message shown on a failed login,
     * come from one place; a client that maps results to its own strings can ignore it.
     */
    public String message() {
        return message;
    }
}
