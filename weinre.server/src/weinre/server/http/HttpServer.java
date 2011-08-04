/*
 * weinre is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2010, 2011 IBM Corporation
 */

package weinre.server.http;

import java.io.IOException;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.component.LifeCycle;

import weinre.server.Main;
import weinre.server.ServerSettings;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.net.InetAddress;
import javax.jmdns.impl.HostInfo;
import java.net.UnknownHostException;

//-------------------------------------------------------------------
public class HttpServer {

    private Main           main;
    private ServerSettings settings;
    private JmDNS          jmdns;
    
    //---------------------------------------------------------------
    public HttpServer(Main main, ServerSettings settings) {
        super();
        
        this.main     = main;
        this.settings = settings;
    }
    
    //---------------------------------------------------------------
    public Server run() throws Exception {
        String hostName     = settings.getBoundHostValue();
        String niceHostName = settings.getNiceHostName();
        
        SelectChannelConnector connector = new SelectChannelConnector();
        
        connector.setHost(hostName);
        connector.setPort(settings.getHttpPort());
        connector.setReuseAddress(settings.reuseAddr());
        connector.setResolveNames(true);

        Server server = new Server();
        server.setConnectors(new Connector[] { connector });
        server.addLifeCycleListener(new LifeCycle.Listener() {
            public void lifeCycleStarting(LifeCycle event) {}
            public void lifeCycleStarted(LifeCycle event) { main.serverStarted(); }
            public void lifeCycleFailure(LifeCycle event, Throwable cause) {}
            public void lifeCycleStopping(LifeCycle event) {}
            public void lifeCycleStopped(LifeCycle event) {}
        });

        // set up the main handlers
        HandlerList handlers = new HandlerList();
        
        // debug handler
//        DebugHandler debugHandler = new DebugHandler();
//        handlers.addHandler(debugHandler);

        HttpSocketHandler httpSocketHandler;

        // client socket handler
        httpSocketHandler = new HttpSocketHandler("/ws/client");
        handlers.addHandler(httpSocketHandler);
        
        // target socket handler
        httpSocketHandler = new HttpSocketHandler("/ws/target");
        handlers.addHandler(httpSocketHandler);

//        runChannelTester();
        
        // handler for /web (static files in jar)
        ClassPathResourceHandler handlerStaticFiles = new ClassPathResourceHandler("web");
        handlers.addHandler(handlerStaticFiles);
        
        // outer collection of handlers for management
        HandlerCollection handlerOuter = new HandlerCollection();
        
        // add the main handlers
        handlerOuter.addHandler(handlers);

        // add a request logger
        RequestLogHandler requestLogHandler = new FilteredRequestLogHandler();
        
        NCSARequestLog requestLog = new NCSARequestLog();
        requestLog.setRetainDays(90);
        requestLog.setAppend(true);
        requestLog.setExtended(false);
        requestLog.setLogTimeZone("GMT");
        requestLogHandler.setRequestLog(requestLog);
        
        handlerOuter.addHandler(requestLogHandler);
        
        // serve with the outer handler
        server.setHandler(handlerOuter);

        // start the JS thread
//        JavaScriptRunner.runThreaded("server/main.js", null, null);
        
        // start the server
        try {
            server.start();
        }
        catch (IOException e) {
            throw e;
        }
        
        if (settings.useZeroConf()) {
            setupZeroConf();
        }
        
        Main.info("HTTP server started at http://" + niceHostName + ":" + settings.getHttpPort());
        
        return server;
    }

    String getZeroConfName() throws UnknownHostException {
        String name = settings.getZeroConf();
        String boundHost = settings.getBoundHostValue();
        if (Boolean.valueOf(name)) {            
            if (boundHost == null || (boundHost.indexOf('.') > 0)) {
                InetAddress localhost = InetAddress.getLocalHost();
                String fullname = localhost.getHostName();
                String[] parts = fullname.split("\\.");
                name = parts[0];
            } else {
                name = boundHost;
            }
        }
        return name;        
    }
    
    byte[] getZeroConfAddress() throws UnknownHostException {
        InetAddress address;
        String boundHost = settings.getBoundHostValue();

        if (boundHost == null) {
            HostInfo kludge = HostInfo.newHostInfo(null, null, null);
            address = kludge.getInetAddress();
        } else {
            address = InetAddress.getByName(boundHost); // name or address
        }
        return address.getAddress();
    }
    
    void setupZeroConf() throws IOException {

        String name = getZeroConfName();
        InetAddress address = InetAddress.getByAddress(name, getZeroConfAddress());
        jmdns = JmDNS.create(address, name);
        
        int port = settings.getHttpPort();
        Main.info("ZeroConf http://" + jmdns.getHostName() + ":" + port);  // TODO get name in callback
        
        String serviceName = "weinre";
        ServiceInfo weinreService = ServiceInfo.create("_weinre._tcp.local.", serviceName, port, "");
        ServiceInfo httpService = ServiceInfo.create("_http._tcp.local.", serviceName, port, "");
        
        jmdns.registerService(weinreService);
        jmdns.registerService(httpService);
        
    }
        
}
