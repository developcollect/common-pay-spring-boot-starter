package com.developcollect.commonpay.autoconfig.controller;

import cn.hutool.core.util.XmlUtil;
import org.w3c.dom.Document;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zak
 * @since 1.0.0
 */
class BaseController {


    protected Map<String, String> getParams(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> params = new HashMap<>(24);
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            params.put(entry.getKey(), entry.getValue()[0]);
        }
        return params;
    }

    protected Map<String, String> getParamsFromXmlBody(HttpServletRequest request, String rootTag) throws IOException {
        Document document = XmlUtil.readXML(request.getInputStream());
        return toMap(document, rootTag);
    }

    protected Map<String, String> getParamsFromXmlStr(String xmlStr, String rootTag) throws IOException {
        Document document = XmlUtil.parseXml(xmlStr);
        return toMap(document, rootTag);
    }

    private Map<String, String> toMap(Document document, String rootTag) {
        Map<String, Object> map = XmlUtil.xmlToMap(document);
        if (rootTag != null) {
            map = (Map<String, Object>) map.get(rootTag);
        }
        Map<String, String> params = new HashMap<>(24);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            params.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return params;
    }

}
