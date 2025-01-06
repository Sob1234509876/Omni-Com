package top.sob.platform.api.models.auth;

import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.model.Spec;

import java.util.Map;

public interface SpecAuthorizer extends Spec<HttpExchange, Boolean>, Authorizer {

    Map<Class<?>, SpecAuthorizer> SPECS = Map.of();

    @Override
    default @Unmodifiable Map<Class<?>, SpecAuthorizer> getSpecs() {
        return SPECS;
    }

    @Override
    default Boolean apply(@NotNull HttpExchange httpExchange) {
        return authorize(httpExchange);
    }
}
