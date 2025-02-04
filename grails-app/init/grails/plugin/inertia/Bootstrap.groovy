/*
 * Copyright 2022-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.inertia

import grails.core.GrailsApplication
import grails.plugin.inertia.ssr.BundleDetector
import grails.plugin.inertia.ssr.ServerSideRenderConfig
import groovy.transform.CompileStatic

/**
 * A class that handles startup and shutdown tasks.
 *
 * @author Mattias Reichel
 * @since 1.0.0
 */
@CompileStatic
class BootStrap {

    GrailsApplication grailsApplication

    private boolean ssrEnabled = false
    private Process ssrProcess

    def init = { servletContext ->

        ssrEnabled = grailsApplication.config.getProperty("${ServerSideRenderConfig.PREFIX}.enabled", Boolean, false)
        if(ssrEnabled) {
            startSSR()
            Runtime.runtime.addShutdownHook(new Thread({ stopSSR() }))
        }
    }

    void startSSR() {

        log.debug 'Trying to start SSR process...'

        def bundle = BundleDetector.detect(grailsApplication.config)
        def configuredBundle = grailsApplication.config.getProperty("${ServerSideRenderConfig.PREFIX}.bundle", String, null)

        if(!bundle) {
            log.error configuredBundle
                    ? /Inertia SSR bundle not found at configured path: "${configuredBundle}"/
                    : /Inertia SSR bundle not found. Set the correct Inertia SSR bundle path in you ${ServerSideRenderConfig.PREFIX}.bundle config./
            return
        } else if(configuredBundle && bundle != configuredBundle) {
            log.warn(/Inertia SSR bundle found at configured path: "${configuredBundle}"/)
            log.warn(/Using a default bundle instead: "$bundle"/)
        }

        ssrProcess = new ProcessBuilder().inheritIO().command('node',  bundle).start()
        def url = new URL(grailsApplication.config.getProperty("${ServerSideRenderConfig.PREFIX}.url", String))
        waitForSsrServer(url.host, url.port)
        log.debug "SSR process started with pid: ${ssrProcess.pid()}"
    }

    void stopSSR() {
        log.debug "Stopping SSR process with pid: ${ssrProcess?.pid()}"
        ssrProcess?.destroy()
    }

    void waitForSsrServer(String host, int port, int timeoutMs = 1000, int maxRetries = 10) {
        boolean portOpen = false
        int tryNumber = 0
        while (!portOpen && ++tryNumber <= maxRetries) {
            log.debug "Checking if SSR server is up on $host:$port ($tryNumber/$maxRetries)..."
            try (Socket socket = new Socket(host, port)) {
                // If the socket is successfully created, the port is open
                log.debug "SSR server is up!"
                portOpen = true
            } catch (IOException ignore) {
                // Port is not open yet, wait for some time before retrying
                log.debug "SSR server is not responding yet, retrying in $timeoutMs ms..."
                try {
                    Thread.sleep(timeoutMs)
                } catch (InterruptedException ex) {
                    ex.printStackTrace()
                }
            }
        }
        if (!portOpen) {
            throw new IllegalStateException("SSR server is not responding on $host:$port")
        }
    }
}
