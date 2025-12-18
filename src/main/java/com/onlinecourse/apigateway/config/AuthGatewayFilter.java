//package com.onlinecourse.apigateway.config;
//
//import jakarta.servlet.*;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletRequestWrapper;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.stereotype.Component;
//import org.springframework.util.AntPathMatcher;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Enumeration;
//import java.util.List;
//
//@Component
//
//public class AuthFilter extends OncePerRequestFilter {
//
//    private final JwtUtil jwtUtil;
//    private final AntPathMatcher pathMatcher = new AntPathMatcher();
//    private final PermitProperties permitProperties;
//
//
//    public AuthFilter(JwtUtil jwtUtil, PermitProperties permitProperties) {
//        this.jwtUtil = jwtUtil;
//        this.permitProperties = permitProperties;
//    }
//
//    private boolean isPermitPath(String path) {
//        List<String> permitPaths = permitProperties.getPaths();
//
//        if (permitPaths == null || permitPaths.isEmpty()) return false;
//        for (String p : permitPaths) {
//            if (pathMatcher.match(p.trim(), path)) return true;
//        }
//        return false;
//    }
//    @Override
//    protected void doFilterInternal(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            FilterChain filterChain
//    ) throws ServletException, IOException {
//
//
//        String path = request.getRequestURI();
//        System.out.println(">>> AuthFilter running on path: " + path);
//
//        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPermitPath(path)) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        String authHeader = request.getHeader("Authorization");
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }
//
//        var claims = jwtUtil.validateToken(authHeader.substring(7));
//        if (claims == null) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }
//
////        // ========== THÊM LOG ĐỂ DEBUG NGAY TẠI ĐÂY ==========
////        System.out.println("--- [GATEWAY AUTH FILTER DEBUG] ---");
////        System.out.println("Token Validated. Subject (User ID): " + claims.getSubject());
////        // In ra chính xác object "role" mà filter thấy được
////        System.out.println("Claims Role (Object): " + claims.get("role"));
////        System.out.println("--- [END AUTH FILTER DEBUG] ---");
//
//        HttpServletRequest wrapper = new HttpServletRequestWrapper(request) {
//
//            private final String userId = claims.getSubject();
//            private final String userRole = claims.get("role") != null ? claims.get("role").toString() : null;
//
//            @Override
//            public String getHeader(String name) {
//                if ("X-User-Id".equalsIgnoreCase(name)) {
//                    return userId;
//                }
//                if ("X-User-Role".equalsIgnoreCase(name)) {
//                    return userRole;
//                }
//                return super.getHeader(name);
//            }
//
//            @Override
//            public Enumeration<String> getHeaders(String name) {
//                if ("X-User-Id".equalsIgnoreCase(name)) {
//                    return Collections.enumeration(Collections.singletonList(userId));
//                }
//                if ("X-User-Role".equalsIgnoreCase(name) && userRole != null) {
//                    return Collections.enumeration(Collections.singletonList(userRole));
//                }
//                return super.getHeaders(name);
//            }
//
//            @Override
//            public Enumeration<String> getHeaderNames() {
//                List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
//
//                // Thêm header mới nếu nó chưa tồn tại (kiểm tra case-insensitive)
//                if (names.stream().noneMatch("X-User-Id"::equalsIgnoreCase)) {
//                    names.add("X-User-Id");
//                }
//                if (userRole != null && names.stream().noneMatch("X-User-Role"::equalsIgnoreCase)) {
//                    names.add("X-User-Role");
//                }
//                return Collections.enumeration(names);
//            }
//        };
//
//        filterChain.doFilter(wrapper, response);
//    }
//}

package com.onlinecourse.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AuthGatewayFilter extends AbstractGatewayFilterFactory<AuthGatewayFilter.Config> {

    private final JwtUtil jwtUtil;

    public AuthGatewayFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    public static class Config {
        // Có thể thêm cấu hình sau này nếu cần
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            System.out.println("path: " + path);
            // Only skip auth for truly public endpoints (login, register, internal service calls)
            if (path.startsWith("/api/user-service/user/login")
                    || path.startsWith("/api/user-service/user/register")
                    || path.startsWith("/api/user-service/user/instructor")
                    || path.startsWith("/api/user-service/user/enrolled")
                    || path.startsWith("/api/course-service/course/alls")
                    || path.startsWith("/swagger-ui")
                    || path.startsWith("/v3/api-docs")
                    || path.startsWith("/actuator")) {
                return chain.filter(exchange);
            }

            // For all other paths (including /user, /profile, /upload-avatar), require auth and add headers
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            var claims = jwtUtil.validateToken(authHeader.substring(7));
            if (claims == null) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String userId = claims.getSubject();
            String role = claims.get("role") != null ? claims.get("role").toString() : null;

            // Thêm các header mới
            var mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .then(Mono.fromRunnable(() ->
                            System.out.println("✅ Added X-User-Id: " + userId + ", Role: " + role)
                    ));
        };
    }
}
