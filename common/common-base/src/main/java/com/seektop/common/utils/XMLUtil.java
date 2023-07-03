
package com.seektop.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class XMLUtil {

  private static final String XML_VERSION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  @SuppressWarnings("unchecked")
  public static void toXML(Map<String, Object> map, StringBuilder xml) {
    Iterator<String> it = map.keySet().iterator();
    String key = null;
    Object value = null;
    while (it.hasNext()) {
      key = it.next();
      value = map.get(key);
      if (value instanceof Map) {
        xml.append("<").append(key).append(">");
        toXML((Map<String, Object>) value, xml);
        xml.append("</").append(key).append(">");
      } else if (value instanceof LinkedList) {
        LinkedList<Object> values = (LinkedList<Object>) value;
        Iterator<Object> valueIt = values.iterator();
        while (valueIt.hasNext()) {
          xml.append("<").append(key).append(">");
          Object obj = valueIt.next();
          if (obj instanceof Map) {
            toXML((Map<String, Object>) obj, xml);
          } else {
            xml.append(obj);
          }
          xml.append("</").append(key).append(">");
        }
      } else {
        xml.append("<").append(key).append(">");
        xml.append(value);
        xml.append("</").append(key).append(">");
      }
    }
  }

  public static Map<String, Object> fromXML(String xml) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    SAXBuilder builder = new SAXBuilder();
    Document document = null;
    try {
      document = builder.build(new StringReader(xml));
    } catch (JDOMException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    Element root = document.getRootElement();
    fromXML(root, map);
    return map;
  }

  @SuppressWarnings("unchecked")
  public static void fromXML(Element element, Map<String, Object> map) {
    String name = element.getName();
    Object value = null;
    Object existing = map.get(name);
    if (element.getChildren() == null || element.getChildren().isEmpty()) {
      value = element.getText();
    } else {
      Map<String, Object> subMap = new LinkedHashMap<String, Object>();
      Iterator<Element> it = element.getChildren().iterator();
      Element subElement = null;
      while (it.hasNext()) {
        subElement = it.next();
        fromXML(subElement, subMap);
      }
      value = subMap;
    }
    if (existing == null) {
      map.put(name, value);
    } else if (existing instanceof LinkedList) {
      LinkedList<Object> exists = (LinkedList<Object>) existing;
      exists.add(value);
      map.put(name, exists);
    } else {
      LinkedList<Object> mapList = new LinkedList<Object>();
      mapList.add(existing);
      mapList.add(value);
      map.put(name, mapList);
    }
  }

  public static String toXML(Map<String, Object> params, String parentName, boolean needVersion) {
    StringBuilder xml = new StringBuilder();
    if (needVersion) {
      xml.append(XML_VERSION);
    }
    if (parentName != null) {
      xml.append("<").append(parentName).append(">");
      toXML(params, xml);
      xml.append("</").append(parentName).append(">");
    } else {
      toXML(params, xml);
    }
    return xml.toString();
  }

  public static Map<String, String> fromNode(String node) {
    return fromNode(null, node, false);
  }

  public static Map<String, String> fromNode(Map<String, String> map, String node) {
    return fromNode(map, node, true);
  }

  public static Map<String, String> fromNode(Map<String, String> map, String node, boolean clear) {
    if (map == null) {
      map = new LinkedHashMap<String, String>();
    }
    if (clear) {
      map.clear();
    }
    if (StringUtils.isEmpty(node)) {
      return map;
    }
    String[] data = node.replaceAll("\"", "").split(" ");
    String[] sp = null;
    String lastKey = null;
    for (String d : data) {
      if (d.contains("=")) {
        sp = d.trim().split("=");
        if (sp.length > 1) {
          lastKey = sp[0].trim();
          map.put(sp[0].trim(), sp[1].trim());
        }
      } else if (lastKey != null && !StringUtils.isEmpty(d)) {
        map.put(lastKey, map.get(lastKey) + " " + d.replace("/>", ""));
      }
    }
    return map;
  }

  public static void main(String[] args) {
    String xml =
        "<response><message><version>1.0.0</version><qrCode>https://qr.alipay.com/bax05313pcwrnavgor0o40ac</qrCode></message><signMsg>XyUfoCRUJS7v+oAvBNjE3j0+8SO1mkyKsk0nDhu5LHNRrffE/m5DzKCevz1AqfTFNHBk61f4RzBzV8aaWg7EJ+VehKRYdfnbnuCTcI5hUFqpDbfafPLb23HOk0+JS+KVf3oFoESvrC1YCdcNSi/fV1AFOYLAAWASX6kp0PhKq8FVurGM3tRMfbsJrbpUkNeDmLzej7YdEzHpVv0SrJaQYoZFquezOrr3Bpig84OD5FpaeVVNoqWhjVuwrLmf2U7J/iviE0crZknEIRBQOVGV5ctNOhs/wBKyRH75D7roj5zdEgC4TBklv4QmRvC2QXM03a4BfFgm8GxFsmFSDiWqEA==</signMsg></response>";
    Map<String, Object> map = fromXML(xml);
    System.out.println(map);
    System.out.println(map.get("info"));

  }

}
