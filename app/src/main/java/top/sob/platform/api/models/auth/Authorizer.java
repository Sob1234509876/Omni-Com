package top.sob.platform.api.models.auth;

import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;
import top.sob.platform.api.misc.model.Model;

import java.util.Objects;

public interface Authorizer extends Model<HttpExchange, SpecAuthorizer> {

    default boolean authorize(@NotNull HttpExchange exchange) {

        Objects.requireNonNull(exchange);

        return filter(exchange);
    }

}
