module osl.projects.osa.server {
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.security.config;
    requires spring.security.web;
    requires static lombok;
    requires spring.security.core;
    requires spring.security.crypto;
    requires spring.data.commons;
    requires spring.data.mongodb;
    requires org.mongodb.driver.sync.client;
    requires spring.web;
    requires spring.core;
    requires spring.beans;
    requires osl.projects.osftp.library;
    requires spring.webmvc;
    requires org.slf4j;
    requires org.apache.tomcat.embed.core;
    requires jjwt.api;

    exports io.github.sob1234509876.osa.server.configuration;
    exports io.github.sob1234509876.osa.server.component;
    exports io.github.sob1234509876.osa.server.security;
    exports io.github.sob1234509876.osa.server.service;
    exports io.github.sob1234509876.osa.server.controller;
    exports io.github.sob1234509876.osa.server;
    exports io.github.sob1234509876.osa.server.dao.ftp;
    exports io.github.sob1234509876.osa.server.dao.mongo;
    exports io.github.sob1234509876.osa.server.api;
    exports io.github.sob1234509876.osa.server.security.filter;
}