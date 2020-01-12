package com.alibaba.csp.sentinel.transport.interceptor;


import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class SentinelWebInterceptor implements HandlerInterceptor {

    public static final String SENTINEL_ENTRY_ATTR_KEY = "$$sentinel_web_entry";
    public static final String SPRING_WEB_CONTEXT = "sentinel_spring_web_context";
    private static final String EMPTY_ORIGIN = "";


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String target;
        if (pattern != null) {
            target = (String) pattern;
        } else {
            target = request.getRequestURI();
        }

        if (StringUtil.isEmpty(target)) {
            return true;
        } else {
            ContextUtil.enter(SPRING_WEB_CONTEXT, EMPTY_ORIGIN);
            try {
                Entry entry = SphU.entry(target, 1, EntryType.IN);
                request.setAttribute(SENTINEL_ENTRY_ATTR_KEY, entry);
                return true;
            } catch (BlockException e) {
                writeBlockedResponse(response,429);
                return false;
            }
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) throws Exception {
        Object webEntry = request.getAttribute(SENTINEL_ENTRY_ATTR_KEY);
        if (webEntry != null) {
            Entry entry = (Entry) webEntry;
            if (exception != null) {
                Tracer.traceEntry(exception, entry);
            }
            entry.exit();
            request.removeAttribute(SENTINEL_ENTRY_ATTR_KEY);
        }
    }

    private static void writeBlockedResponse(HttpServletResponse response, int httpStatus) throws IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setStatus(httpStatus);
        PrintWriter out = response.getWriter();
        //TODO change the response body
        out.print("Blocked by Sentinel (flow limiting) :( ");
        out.flush();
        out.close();
    }

}
