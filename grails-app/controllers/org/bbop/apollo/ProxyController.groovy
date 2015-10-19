package org.bbop.apollo

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@Transactional(readOnly = true)
class ProxyController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def permissionService

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Proxy.list(params), model: [proxyInstanceCount: Proxy.count()]
    }

    def show(Proxy proxyInstance) {
        respond proxyInstance
    }

    def create() {
        params.active = true
        respond new Proxy(params)
    }

    /**
     * @return
     */
    def request(String url) {
        // only a logged-in user can use the proxy
        User currentUser = permissionService.currentUser
        if (!currentUser) {
            log.warn "Attempting to proxy ${url} without a logged-in user with params ${params}"
            render status: UNAUTHORIZED
            return
        }
        String referenceUrl = URLDecoder.decode(url, "UTF-8")
        Proxy proxy = Proxy.findByReferenceUrl(referenceUrl)


        log.info "using proxy ${proxy?.targetUrl}"

        String targetUrl = proxy ? proxy.targetUrl : referenceUrl

        targetUrl += "?"+request.queryString
        URL returnUrl = new URL(targetUrl)

        log.debug "input URI ${request.requestURI}"
        log.info "request url ${referenceUrl}?${request.getQueryString()}"
        log.info "return url: ${returnUrl}"
        render text: returnUrl.text
    }

    @Transactional
    def save(Proxy proxyInstance) {
        if (proxyInstance == null) {
            notFound()
            return
        }

        if (proxyInstance.hasErrors()) {
            respond proxyInstance.errors, view: 'create'
            return
        }

        proxyInstance.save flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'proxy.label', default: 'Proxy'), proxyInstance.id])
                redirect proxyInstance
            }
            '*' { respond proxyInstance, [status: CREATED] }
        }
    }

    def edit(Proxy proxyInstance) {
        respond proxyInstance
    }

    @Transactional
    def update(Proxy proxyInstance) {
        if (proxyInstance == null) {
            notFound()
            return
        }

        if (proxyInstance.hasErrors()) {
            respond proxyInstance.errors, view: 'edit'
            return
        }

        proxyInstance.save flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'Proxy.label', default: 'Proxy'), proxyInstance.id])
                redirect proxyInstance
            }
            '*' { respond proxyInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(Proxy proxyInstance) {

        if (proxyInstance == null) {
            notFound()
            return
        }

        proxyInstance.delete flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'Proxy.label', default: 'Proxy'), proxyInstance.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'proxy.label', default: 'Proxy'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }
}
