module osl.projects.osc.server {
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.security.config;
    requires spring.security.web;
    requires static lombok;
    requires org.jetbrains.annotations;

    requires spring.security.core;
    requires spring.data.commons;
    requires spring.data.mongodb;
    requires org.mongodb.driver.sync.client;
    requires spring.web;
    requires spring.core;
    requires spring.beans;
    requires spring.webmvc;
    requires spring.websocket;
    requires osl.projects.osa.api;
    requires osl.projects.osc.api;
    requires osl.projects.osftp.library;
    requires jjwt.api;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires org.apache.tomcat.embed.core;

    exports io.github.sob1234509876.osc.server;
    exports io.github.sob1234509876.osc.server.annotation;
    exports io.github.sob1234509876.osc.server.component;
    exports io.github.sob1234509876.osc.server.configuration;
    exports io.github.sob1234509876.osc.server.controller;
    exports io.github.sob1234509876.osc.server.dao.mongo;
    exports io.github.sob1234509876.osc.server.security;
    exports io.github.sob1234509876.osc.server.security.filter;
    exports io.github.sob1234509876.osc.server.service;
}
