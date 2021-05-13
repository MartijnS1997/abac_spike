package org.springframework.data.querydsl.binding;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.util.Optional;
import java.util.function.BiConsumer;
import javax.persistence.EntityManager;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.query.Jpa21Utils;
import org.springframework.data.jpa.repository.query.JpaEntityGraph;
import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.MutableQueryHints;
import org.springframework.data.jpa.repository.support.QueryHints;
import org.springframework.data.util.Optionals;
import org.springframework.util.Assert;

class DefaultQueryHints implements QueryHints {
    private final JpaEntityInformation<?, ?> information;
    private final CrudMethodMetadata metadata;
    private final Optional<EntityManager> entityManager;
    private final boolean forCounts;

    private DefaultQueryHints(JpaEntityInformation<?, ?> information, CrudMethodMetadata metadata, Optional<EntityManager> entityManager, boolean forCounts) {
        this.information = information;
        this.metadata = metadata;
        this.entityManager = entityManager;
        this.forCounts = forCounts;
    }

    public static QueryHints of(JpaEntityInformation<?, ?> information, CrudMethodMetadata metadata) {
        Assert.notNull(information, "JpaEntityInformation must not be null!");
        Assert.notNull(metadata, "CrudMethodMetadata must not be null!");
        return new DefaultQueryHints(information, metadata, Optional.empty(), false);
    }

    public QueryHints withFetchGraphs(EntityManager em) {
        return new DefaultQueryHints(this.information, this.metadata, Optional.of(em), this.forCounts);
    }

    public QueryHints forCounts() {
        return new DefaultQueryHints(this.information, this.metadata, this.entityManager, true);
    }

    public void forEach(BiConsumer<String, Object> action) {
        this.combineHints().forEach(action);
    }

    private QueryHints combineHints() {
        return QueryHints.from(new QueryHints[]{this.forCounts ? this.metadata.getQueryHintsForCount() : this.metadata.getQueryHints(), this.getFetchGraphs()});
    }

    private QueryHints getFetchGraphs() {
        return (QueryHints)Optionals.mapIfAllPresent(this.entityManager, this.metadata.getEntityGraph(), (em, graph) -> {
            return Jpa21Utils.getFetchGraphHint(em, this.getEntityGraph(graph), this.information.getJavaType());
        }).orElse(new MutableQueryHints());
    }

    private JpaEntityGraph getEntityGraph(EntityGraph entityGraph) {
        String fallbackName = this.information.getEntityName() + "." + this.metadata.getMethod().getName();
        return new JpaEntityGraph(entityGraph, fallbackName);
    }
}
