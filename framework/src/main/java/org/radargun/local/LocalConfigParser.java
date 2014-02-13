package org.radargun.local;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.radargun.CacheWrapperStressor;
import org.radargun.config.Evaluator;
import org.radargun.config.DomConfigParser;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.config.StressorHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Mircea.Markus@jboss.com
 */
public class LocalConfigParser {

   private static Log log = LogFactory.getLog(LocalConfigParser.class);

   List<ReportItem> all = new ArrayList<ReportItem>();


   public LocalBenchmark parse(String config) throws Exception {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document document;
      try {
         document = builder.parse(config);
      }
      catch (Exception e) {
         throw new IllegalStateException(e);
      }

      LocalBenchmark result = new LocalBenchmark();
      Element configRoot = (Element) document.getElementsByTagName("local-bench-config").item(0);
      parseBenchmarkElement(result, configRoot);
      parseProductsElement(configRoot, result);
      parseReporting(configRoot, result);
      return result;
   }

   private void parseBenchmarkElement(LocalBenchmark result, Element configRoot) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      Element benchmark = (Element) configRoot.getElementsByTagName("benchmark").item(0);
      for (int i = 0; i < benchmark.getChildNodes().getLength(); i++) {
         Node node = benchmark.getChildNodes().item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            CacheWrapperStressor stressor = StressorHelper.getStressor(nodeEl.getNodeName());
            Map<String, String> attrValues = new HashMap<String, String>();
            for (int j = 0; j < nodeEl.getAttributes().getLength(); j++) {
               Attr attr = (Attr) nodeEl.getAttributes().item(j);
               attrValues.put(attr.getName(), attr.getValue());
            }
            PropertyHelper.setProperties(stressor, attrValues, false);
            InitHelper.init(stressor);
            result.addStressor(stressor);
         }
      }
   }

   private void parseProductsElement(Element configRoot, LocalBenchmark localBenchmark) {
      Element productsEl = (Element) configRoot.getElementsByTagName("products").item(0);
      NodeList productsChildEl = productsEl.getChildNodes();
      for (int i = 0; i < productsChildEl.getLength(); i++) {
         Node node = productsChildEl.item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            String productName = nodeEl.getNodeName();
            NodeList configs = nodeEl.getElementsByTagName("config");
            List<Map<String, String>> configNames = new ArrayList<Map<String, String>>();

            for (int configIndex = 0; configIndex < configs.getLength(); configIndex++) {
               Element configEl = (Element) configs.item(configIndex);
               Map<String, String> configAttrs = new HashMap<String, String>();
               DomConfigParser.addDirectAttributes(configAttrs, configEl, "");
               DomConfigParser.addWrapperAttributes(configAttrs, configEl, "");

               configNames.add(configAttrs);
               all.add(new ReportItem(productName, configAttrs.get("name")));
            }
            localBenchmark.addProductConfig(productName, configNames);
         }
      }
   }


   private void parseReporting(Element configRoot, LocalBenchmark localBenchmark) {
      Element reportsEl = (Element) configRoot.getElementsByTagName("reports").item(0);
      NodeList reportElList = reportsEl.getElementsByTagName("report");
      for (int i = 0; i < reportElList.getLength(); i++) {
         if (reportElList.item(i) instanceof Element) {
            ReportDesc reportDesc = new ReportDesc();
            Element thisReportEl = (Element) reportElList.item(i);
            if (thisReportEl.getAttribute("includeAll") != null) {
               String inclAll = Evaluator.parseString(thisReportEl.getAttribute("includeAll"));
               if (inclAll.equalsIgnoreCase("true")) {
                  reportDesc.setIncludeAll(true);
                  reportDesc.addReportItems(all);
                  localBenchmark.addReportDesc(reportDesc);
                  reportDesc.setReportName(Evaluator.parseString(thisReportEl.getAttribute("name")));
                  continue;
               }
            }

            NodeList itemsEl = thisReportEl.getElementsByTagName("item");
            for (int j = 0; j < itemsEl.getLength(); j++) {
               Element itemEl = (Element) itemsEl.item(j);
               String productName = Evaluator.parseString(itemEl.getAttribute("product"));
               String productConfig = Evaluator.parseString(itemEl.getAttribute("config"));
               reportDesc.addReportItem(productName, productConfig);
            }
            reportDesc.setReportName(Evaluator.parseString(thisReportEl.getAttribute("name")));
            localBenchmark.addReportDesc(reportDesc);
         }
      }
   }


}
