package io.github.sob1234509876.osa.server.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;

public interface StringToGrantedAuthorityConverter extends Converter<String, GrantedAuthority> {
}
