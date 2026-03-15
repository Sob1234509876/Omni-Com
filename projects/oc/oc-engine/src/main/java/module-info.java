module oc.projects.oc.engine {
    requires static lombok;
    requires oc.projects.oc.engine.api;
    requires spring.core;
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.beans;
    requires jakarta.annotation;
    requires org.slf4j;
    requires spring.data.commons;
    requires spring.data.mongodb;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;

    exports io.github.sob1234509876.oc.engine;
    exports io.github.sob1234509876.oc.engine.event;
    exports io.github.sob1234509876.oc.engine.rm;
    exports io.github.sob1234509876.oc.engine.rm.util;
    exports io.github.sob1234509876.oc.engine.configuration;
    exports io.github.sob1234509876.oc.engine.event.collection;
    exports io.github.sob1234509876.oc.engine.event.bus;
    exports io.github.sob1234509876.oc.engine.bean;
    exports io.github.sob1234509876.oc.engine.misc;
    exports io.github.sob1234509876.oc.engine.rm.collection;
    exports io.github.sob1234509876.oc.engine.dao.collection;
    exports io.github.sob1234509876.oc.engine.dao.repository;
    exports io.github.sob1234509876.oc.engine.dao.mongo;
}