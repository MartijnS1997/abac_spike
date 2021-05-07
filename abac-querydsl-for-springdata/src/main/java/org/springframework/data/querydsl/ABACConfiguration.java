package org.springframework.data.querydsl;

import static java.lang.String.format;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import be.heydari.lib.expressions.BoolPredicate;
import be.heydari.lib.expressions.Conjunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.security.core.parameters.P;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.UrlPathHelper;

import be.heydari.lib.converters.protobuf.ProtobufUtils;
import be.heydari.lib.converters.protobuf.generated.PDisjunction;
import be.heydari.lib.expressions.Disjunction;
import internal.org.springframework.content.rest.utils.RepositoryUtils;

@Configuration
public class ABACConfiguration {

    @Bean
    public ABACExceptionHandler exceptionHandler() {
        return new ABACExceptionHandler();
    }

    @Bean
    public ABACRequestFilter abacFilter(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
        return new ABACRequestFilter(repos, em, tm);
    }

    @Bean
    public FilterRegistrationBean<ABACRequestFilter> abacFilterRegistration(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
        FilterRegistrationBean<ABACRequestFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(abacFilter(repos, em, tm));
        registrationBean.addUrlPatterns("/accountStates/*");
        registrationBean.addUrlPatterns("/content/*");

        return registrationBean;
    }

    public static class ABACRequestFilter implements Filter {

        private final Repositories repos;
        private final EntityManager em;
        private final PlatformTransactionManager tm;

        public ABACRequestFilter(Repositories repos, EntityManager em, PlatformTransactionManager tm) {
            this.repos = repos;
            this.em = em;
            this.tm = tm;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException,
                ServletException {

            HttpServletRequest request = (HttpServletRequest) servletRequest;

            String path = new UrlPathHelper().getLookupPathForRequest(request);
            String[] pathElements = path.split("/");
            RepositoryInformation ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[1]);
            if (ri == null) {
                ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[2]);
            }
            if (ri == null) {
                throw new IllegalStateException(format("Unable to resolve entity class: %s", path));
            }
            Class<?> entityClass = ri.getDomainType();

            EntityInformation ei = JpaEntityInformationSupport.getEntityInformation(entityClass, em);
            if (entityClass != null) {
                EntityContext.setCurrentEntityContext(ei);
            }

            EntityManagerContext.setCurrentEntityContext(em, tm);

            String abacMode = request.getHeader("X-ABAC-Mode");
//            System.out.println("abacMode: " + abacMode);

            // Emad
            switch (abacMode) {
                case "multi":
                    multiModeContext(request);
                    break;
                case "single":
                default:
                    singleModeContext(request);
            }

            filterChain.doFilter(servletRequest, servletResponse);

            ABACContext.clear();
            EntityContext.clear();
        }

        private void singleModeContext(HttpServletRequest request) throws IOException {
            String abacContext = request.getHeader("X-ABAC-Context");
            if (abacContext != null) {
                Disjunction disjunction = decodeDisjunction(abacContext);
                ABACContext.setCurrentAbacContext(Collections.singletonList(disjunction));
            }
        }

        private Disjunction decodeDisjunction(String abacContext) throws InvalidProtocolBufferException {
            byte[] abacContextProtobytes = Base64.getDecoder().decode(abacContext);
            PDisjunction pDisjunction = PDisjunction.newBuilder().mergeFrom(abacContextProtobytes).build();
            Disjunction disjunction = ProtobufUtils.to(pDisjunction, "");
            return disjunction;
        }

        private void multiModeContext(HttpServletRequest request) throws IOException {
//            System.out.println("running multimode context");
            String policyJson = request.getHeader("X-ABAC-Policies");
            ObjectMapper mapper = new ObjectMapper();

            Map<String, String> policyTree = (Map<String, String>) mapper.readValue(policyJson, Map.class);
            String path = request.getServletPath();

            List<Disjunction> disjunctions = new ArrayList<>();

            for (String key: policyTree.keySet()) {
//                System.out.println("key: " + key);
//                System.out.println("path: " + path);
                Pattern pattern = Pattern.compile(key);
                Matcher m = pattern.matcher(path);
                if (m.matches()) {
                    Disjunction disjunction = decodeDisjunction(policyTree.get(key));
//                    System.out.println("adding disjunction " + disjunction);
                    disjunctions.add(disjunction);
                }
            }

            ABACContext.setCurrentAbacContext(disjunctions);
        }
    }
}
