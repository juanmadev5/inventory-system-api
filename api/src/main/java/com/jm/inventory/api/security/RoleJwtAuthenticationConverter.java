package com.jm.inventory.api.security;

import com.jm.inventory.domain.user.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps this application's own {@code role} claim (set by {@link JwtAccessTokenIssuer} at
 * login, exactly one of {@link Role}'s three values) to a {@code ROLE_*}
 * {@link GrantedAuthority}. A missing or unrecognized claim value grants no authority -
 * the request still authenticates, then gets {@code 403} from the first
 * {@code @PreAuthorize} check, per docs/specs/06-roles-and-permissions/requirements.md.
 */
public class RoleJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_CLAIM = "role";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, grantedAuthoritiesFrom(jwt));
    }

    private List<GrantedAuthority> grantedAuthoritiesFrom(Jwt jwt) {
        return roleFrom(jwt.getClaimAsString(ROLE_CLAIM))
                .map(Role::authorityName)
                .map(authorityName -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(authorityName)))
                .orElse(List.of());
    }

    private Optional<Role> roleFrom(String roleName) {
        return Arrays.stream(Role.values()).filter(role -> role.name().equals(roleName)).findFirst();
    }
}
