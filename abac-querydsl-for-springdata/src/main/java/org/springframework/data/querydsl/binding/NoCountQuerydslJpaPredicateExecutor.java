package org.springframework.data.querydsl.binding;


import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.AbstractJPAQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.*;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

public class NoCountQuerydslJpaPredicateExecutor<T> implements QuerydslPredicateExecutor<T> {
    private final QuerydslJpaPredicateExecutor<T> delegate;

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityPath<T> path;
    private final Querydsl querydsl;
    private final EntityManager entityManager;
    private final CrudMethodMetadata metadata;

    public NoCountQuerydslJpaPredicateExecutor(QuerydslJpaPredicateExecutor<T> executor) {
        // (QuerydslJpaPredicateExecutor<T>)

        System.out.println("methods!");

        Class c = executor.getClass();
        for (Method method : c.getDeclaredMethods()) {
                System.out.println(method.getName());
        }

        System.out.println("fields");

        for (Field field : c.getDeclaredFields()) {
                System.out.println(field.getName());
        }

        System.out.println("done printing");

        this.delegate =(QuerydslJpaPredicateExecutor<T>) executor;
        try {
            this.entityInformation = (JpaEntityInformation<T, ?>) getField(this.delegate, "entityInformation");
            this.path = (EntityPath<T>) getField(this.delegate, "path");
            this.querydsl = (Querydsl) getField(this.delegate, "querydsl");
            this.entityManager = (EntityManager) getField(this.delegate, "entityManager");
            this.metadata = (CrudMethodMetadata) getField(this.delegate, "metadata");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private Object getField(QuerydslPredicateExecutor<T> delegate, String field) throws NoSuchFieldException, IllegalAccessException {
        Field f = delegate.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(delegate);
    }

    @Override
    public Optional<T> findOne(Predicate predicate) {
        return this.delegate.findOne(predicate);
    }

    @Override
    public Iterable<T> findAll(Predicate predicate) {
        return this.delegate.findAll(predicate);
    }

    @Override
    public Iterable<T> findAll(Predicate predicate, Sort sort) {
        return this.delegate.findAll(predicate, sort);
    }

    @Override
    public Iterable<T> findAll(Predicate predicate, OrderSpecifier<?>... orderSpecifiers) {
        return this.delegate.findAll(predicate, orderSpecifiers);
    }

    @Override
    public Iterable<T> findAll(OrderSpecifier<?>... orderSpecifiers) {
        return this.delegate.findAll(orderSpecifiers);
    }

    public Page<T> findAll(Predicate predicate, Pageable pageable) {
        Assert.notNull(predicate, "Predicate must not be null!");
        Assert.notNull(pageable, "Pageable must not be null!");

        try {
            Field f = delegate.getClass().getDeclaredField("querydsl");
            f.setAccessible(true);
            Querydsl dsl = (Querydsl) f.get(this);

            JPQLQuery<T> query = dsl.applyPagination(pageable, this.createQuery(predicate).select(this.path));
            List var10000 = query.fetch();
            return PageableExecutionUtils.getPage(var10000, pageable, new LongSupplier() {
                @Override
                public long getAsLong() {
                    return 0;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long count(Predicate predicate) {
        return 0;
    }

    @Override
    public boolean exists(Predicate predicate) {
        return false;
    }

    private JPQLQuery<?> createQuery(Predicate... predicate) {
        Assert.notNull(predicate, "Predicate must not be null!");
        AbstractJPAQuery<?, ?> query = this.doCreateQuery(this.getQueryHints().withFetchGraphs(this.entityManager), predicate);
        CrudMethodMetadata metadata = this.getRepositoryMethodMetadata();
        if (metadata == null) {
            return query;
        } else {
            LockModeType type = metadata.getLockModeType();
            return type == null ? query : query.setLockMode(type);
        }
    }

    private AbstractJPAQuery<?, ?> doCreateQuery(QueryHints hints, @Nullable Predicate... predicate) {
        AbstractJPAQuery<?, ?> query = this.querydsl.createQuery(new EntityPath[]{this.path});
        if (predicate != null) {
            query = (AbstractJPAQuery)query.where(predicate);
        }

        hints.forEach(query::setHint);
        return query;
    }

    @Nullable
    private CrudMethodMetadata getRepositoryMethodMetadata() {
        return this.metadata;
    }

    private QueryHints getQueryHints() {
        return (QueryHints)(this.metadata == null ? QueryHints.NoHints.INSTANCE : DefaultQueryHints.of(this.entityInformation, this.metadata));
    }
}
