module osl.projects.osftp.library {
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.context;
    requires static lombok;
    requires spring.beans;
    requires org.apache.commons.net;
    requires spring.core;
    requires org.slf4j;

    exports io.github.sob1234509876.osftp;
    exports io.github.sob1234509876.osftp.configuration;
    exports io.github.sob1234509876.osftp.dao.ftp;
    exports io.github.sob1234509876.osftp.lbi;
    exports io.github.sob1234509876.osftp.component;
}