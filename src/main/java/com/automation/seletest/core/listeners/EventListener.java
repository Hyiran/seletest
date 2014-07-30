/*
This file is part of the Seletest by Papadakis Giannis <gpapadakis84@gmail.com>.

Copyright (c) 2014, Papadakis Giannis <gpapadakis84@gmail.com>
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.automation.seletest.core.listeners;


import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.proxy.ProxyServer;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.testng.ITestContext;

import com.automation.seletest.core.listeners.Event.WebInitEvent;
import com.automation.seletest.core.listeners.beanUtils.DriverBeanPostProcessor;
import com.automation.seletest.core.selenium.configuration.LocalDriverConfiguration;
import com.automation.seletest.core.selenium.configuration.RemoteDriverConfiguration;
import com.automation.seletest.core.selenium.configuration.WebDriverConfiguration;
import com.automation.seletest.core.selenium.threads.SessionContext;
import com.automation.seletest.core.selenium.webAPI.WebDriverActionsController;
import com.automation.seletest.core.services.CoreProperties;
import com.automation.seletest.core.services.Performance;
import com.automation.seletest.core.spring.ApplicationContextProvider;

/**
 * ApplicationListener for event handling
 * @author Giannis Papadakis (mailTo:gpapadakis84@gmail.com)
 *
 */
@Component
@Slf4j
public class EventListener implements ApplicationListener<ApplicationEvent> {

    @Override
    public void onApplicationEvent(ApplicationEvent event) {

        if (event instanceof WebInitEvent) {
            try {
                new Initialize().initializeWeb(event);
            } catch (Exception e) {
                log.error("Error "+e.getMessage());
            }
        }
    }

    static class Initialize {


        /**
         * Initialize web session
         * @param event
         * @throws Exception
         */
        public void initializeWeb(ApplicationEvent event) throws Exception{
            WebDriverActionsController wdActions = ApplicationContextProvider.getApplicationContext().getBean(WebDriverActionsController.class);
            SessionContext.getSession().setActionscontroller(wdActions);
            WebDriver driver=null;
            ITestContext textcontext=((WebInitEvent) event).getTestcontext();

            /*************************
             **XML PARAMETERS*********
             *************************
             */
            String GridHost=textcontext.getCurrentXmlTest().getParameter(CoreProperties.GRID_HOST.get());
            String GridPort=textcontext.getCurrentXmlTest().getParameter(CoreProperties.GRID_PORT.get());
            String profileDriver=textcontext.getCurrentXmlTest().getParameter(CoreProperties.PROFILEDRIVER.get());

            //Create Application Context for initializing driver based on specified @Profile
            AnnotationConfigApplicationContext app=new AnnotationConfigApplicationContext();
            app.getEnvironment().setActiveProfiles(new String[]{profileDriver});

            //register Configuration classes
            app.register(
                    LocalDriverConfiguration.class,
                    WebDriverConfiguration.class,
                    RemoteDriverConfiguration.class);

            //start Container for bean initialization
            app.refresh();

            //register a bean post processor for merging capabilities
            app.getBeanFactory().addBeanPostProcessor(new DriverBeanPostProcessor());

            DesiredCapabilities cap = (DesiredCapabilities) app.getBean(CoreProperties.CAPABILITIES.get());

            //If performance is enabled
            if(((WebInitEvent) event).isPerformance()){
                Performance perf = ApplicationContextProvider.getApplicationContext().getBean(Performance.class);
                ProxyServer server=perf.proxyServer(new Random().nextInt(5000));
                perf.newHar("Har at: "+event.getTimestamp());
                cap.setCapability(CapabilityType.PROXY, perf.proxy(server));
                SessionContext.getSession().setPerformance(perf);
            }

            //start a driver object with capabilities
            if(profileDriver.contains("Grid")) {
                driver=(WebDriver) app.getBean(CoreProperties.PROFILEDRIVER.get(), new Object[]{GridHost+":"+GridPort,cap});
            } else {
                driver=(WebDriver) app.getBean(CoreProperties.PROFILEDRIVER.get(), new Object[]{cap});
            }

            //Set objects per session
            wdActions.setDriver(driver);//set the driver object for this session
            wdActions.setJsExec((JavascriptExecutor)driver);//sets the Javascript executor
            SessionContext.getSession().setDriverContext(app);//set the new application context for WebDriver

            //get the address of the app under test
            wdActions.getTargetHost(((WebInitEvent) event).getHostUrl());
            SessionContext.setSessionProperties();
        }
    }

}