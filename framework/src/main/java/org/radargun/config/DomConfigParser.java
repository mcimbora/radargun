package org.radargun.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.radargun.Master;
import org.radargun.Stage;
import org.radargun.stages.AbstractStartStage;
import org.radargun.stages.GenerateReportStage;
import org.radargun.utils.Utils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Mircea.Markus@jboss.com
 */
public class DomConfigParser extends ConfigParser {
   
   public MasterConfig parseConfig(String config) throws Exception {
      //the content in the new file is too dynamic, let's just use DOM for now

      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

      Document document;
      try {
         document = builder.parse(config);
      }
      catch (Exception e) {
         throw new IllegalStateException(e);
      }

      Element configRoot = (Element) document.getElementsByTagName("bench-config").item(0);

      ScalingBenchmarkConfig prototype = buildBenchmarkPrototype(configRoot);
      MasterConfig masterConfig = parseMaster(configRoot, prototype);

      parseProductsElement(configRoot, prototype, masterConfig);

      //now add the reporting
      parseReporting(configRoot, masterConfig);

      return masterConfig;

   }


   private void parseReporting(Element configRoot, MasterConfig masterConfig) {
      Element reportsEl = (Element) configRoot.getElementsByTagName("reports").item(0);
      NodeList reportElList = reportsEl.getElementsByTagName("report");
      FixedSizeBenchmarkConfig reportBenchmark = new FixedSizeBenchmarkConfig();
      reportBenchmark.setProductName("report");

      masterConfig.addBenchmark(reportBenchmark);
      for (int i = 0; i < reportElList.getLength(); i++) {
         if (reportElList.item(i) instanceof Element) {
            Element thisReportEl = (Element) reportElList.item(i);
            GenerateReportStage generateReportStage = new GenerateReportStage();
            reportBenchmark.addStage(generateReportStage);

            NamedNodeMap attributes = thisReportEl.getAttributes();
            Map<String, String> attrToSet = new HashMap<String, String>();
            boolean includeAll = false;
            for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
               Attr attr = (Attr) attributes.item(attrIndex);
               if ("includeAll".equals(attr.getName())) {
                  includeAll = "true".equalsIgnoreCase(attr.getValue());
               } else {
                  attrToSet.put(attr.getName(), Evaluator.parseString(attr.getValue()));
               }
            }
            PropertyHelper.setProperties(generateReportStage, attrToSet, false);
            InitHelper.init(generateReportStage);
            if (includeAll) {
               continue;
            }

            NodeList itemsEl = thisReportEl.getElementsByTagName("item");
            for (int j = 0; j < itemsEl.getLength(); j++) {
               Element itemEl = (Element) itemsEl.item(j);
               String productName = Evaluator.parseString(itemEl.getAttribute("product"));
               String productConfig = Evaluator.parseString(itemEl.getAttribute("config"));
               generateReportStage.addReportFilter(productName, productConfig);
            }
         }
      }
   }

   private void parseProductsElement(Element configRoot, ScalingBenchmarkConfig prototype, MasterConfig masterConfig) {
      Element productsEl = (Element) configRoot.getElementsByTagName("products").item(0);
      NodeList productsChildEl = productsEl.getChildNodes();
      for (int i = 0; i < productsChildEl.getLength(); i++) {
         Node node = productsChildEl.item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            String productName = nodeEl.getNodeName();
            if ("product".equalsIgnoreCase(productName)) {
               productName = Evaluator.parseString(nodeEl.getAttribute("name"));
            }
            NodeList configs = nodeEl.getElementsByTagName("config");
            for (int configIndex = 0; configIndex < configs.getLength(); configIndex++) {
               Element configEl = (Element) configs.item(configIndex);
               String config = configEl.getAttribute("name");
               String configName = Utils.fileName2Config(config);
               String configFile = configEl.getAttribute("file");
               if (configFile.isEmpty()) configFile = config;

               Map<String, String> properties = new HashMap<String, String>();
               addDirectAttributes(properties, configEl, ""); // parse own
               addWrapperAttributes(properties, configEl, "");
               addSitesAttributes(properties, configEl);

               ScalingBenchmarkConfig clone = prototype.clone();
               masterConfig.addBenchmark(clone);
               clone.setProductName(productName);
               clone.setConfigName(configName);
               updateStartupStage(configFile, clone, properties);
            }
         }
      }
   }

   public static void addDirectAttributes(Map<String, String> properties, Element element, String prefix) {
      NamedNodeMap attributes = element.getAttributes();
      for (int j = 0; j < attributes.getLength(); j++) {
         String name = attributes.item(j).getNodeName();
         String value = Evaluator.parseString(attributes.item(j).getNodeValue());
         properties.put(prefix + name, value);
      }
   }

   public static void addWrapperAttributes(Map<String, String> properties, Element element, String prefix) {
      NodeList childList = element.getChildNodes();
      for (int i = 0; i < childList.getLength(); ++i) {
         if (childList.item(i) instanceof Element) {
            Element child = (Element) childList.item(i);
            if (child.getNodeName().equalsIgnoreCase("wrapper")) {
               String wrapperClass = child.getAttribute("class");
               if (wrapperClass != null && !wrapperClass.isEmpty()) {
                  properties.put(prefix + "wrapper", wrapperClass);
               }
               NodeList wrapperProps = child.getChildNodes();
               for (int j = 0; j < wrapperProps.getLength(); ++j) {
                  if (wrapperProps.item(j) instanceof Element) {
                     Element prop = (Element) wrapperProps.item(j);
                     if (!prop.getNodeName().equalsIgnoreCase("property")) {
                        throw new IllegalArgumentException();
                     }
                     String name = prop.getAttribute("name");
                     String value = Evaluator.parseString(prop.getAttribute("value"));
                     if (name == null || name.isEmpty()) throw new IllegalArgumentException();
                     properties.put(prefix + name, value);
                  }
               }
            }
         }
      }
   }

   public static void addSitesAttributes(Map<String, String> properties, Element configEl) {
      NodeList childList = configEl.getChildNodes();
      int siteIndex = 0;
      for (int i = 0; i < childList.getLength(); ++i) {
         if (childList.item(i) instanceof Element) {
            Element child = (Element) childList.item(i);
            if (child.getNodeName().equalsIgnoreCase("site")) {
               addDirectAttributes(properties, child, "site[" + siteIndex + "].");
               addWrapperAttributes(properties, child, "site[" + siteIndex + "].");
               siteIndex++;
            }
         }
      }
   }

   private MasterConfig parseMaster(Element configRoot, ScalingBenchmarkConfig prototype) {
      MasterConfig masterConfig;
      Element masterEl = (Element) configRoot.getElementsByTagName("master").item(0);
      String bindAddress = Evaluator.parseString(masterEl.getAttribute("bindAddress"));
      int port = masterEl.getAttribute("port") != null ? Evaluator.parseInt(masterEl.getAttribute("port")) : Master.DEFAULT_PORT;
      masterConfig = new MasterConfig(port, bindAddress, prototype.getMaxSize());
      return masterConfig;
   }

   private void updateStartupStage(String configFile, ScalingBenchmarkConfig scaling, Map<String, String> properties) {
      for (FixedSizeBenchmarkConfig fixed : scaling.getBenchmarks()) {
         for (Stage st : fixed.getStages()) {
            if (st instanceof AbstractStartStage) {
               AbstractStartStage ass = (AbstractStartStage) st;
               ass.setConfigProperties(properties);
               ass.setProductConfig(scaling.getProductName(), scaling.getConfigName(), configFile);
            }
         }
      }
   }

   private ScalingBenchmarkConfig buildBenchmarkPrototype(Element configRoot) {
      ScalingBenchmarkConfig prototype;
      prototype = new ScalingBenchmarkConfig();
      Element benchmarkEl = (Element) configRoot.getElementsByTagName("benchmark").item(0);
      prototype.setInitSize(Evaluator.parseInt(benchmarkEl.getAttribute("initSize")));
      prototype.setMaxSize(Evaluator.parseInt(benchmarkEl.getAttribute("maxSize")));
      String inc = Evaluator.parseString(benchmarkEl.getAttribute("increment")).trim();
      ScalingBenchmarkConfig.IncrementMethod incMethod = ScalingBenchmarkConfig.IncrementMethod.ADD;
      int incCount = 0;
      if (inc.startsWith("*")) {
         inc = inc.substring(1).trim();
         incMethod = ScalingBenchmarkConfig.IncrementMethod.MULTIPLY;
      }
      try {
         incCount = Integer.parseInt(inc);
      } catch (NumberFormatException e) {
         throw new IllegalArgumentException("Cannot parse increment!", e);
      }
      prototype.setIncrement(incCount, incMethod);
      Collection<FixedSizeBenchmarkConfig> benchmarks = prototype.initBenchmarks();

      NodeList childNodes = benchmarkEl.getChildNodes();
      for (FixedSizeBenchmarkConfig fixedPrototype : benchmarks) {
         System.setProperty("benchmark.activeSize", String.valueOf(fixedPrototype.getSize()));
         System.setProperty("benchmark.maxSize", String.valueOf(fixedPrototype.getMaxSize()));
         for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element) {
               addStage(fixedPrototype, (Element) child);
            }
         }
      }
      return prototype;
   }


   private void addStage(FixedSizeBenchmarkConfig prototype, Element element) {
      if (element.getNodeName().equalsIgnoreCase("Repeat")) {
         String timesStr = element.getAttribute("times");
         String fromStr = element.getAttribute("from");
         String toStr = element.getAttribute("to");
         String incStr = element.getAttribute("inc");
         String repeatName = element.getAttribute("name");
         if ((timesStr.isEmpty() && (fromStr.isEmpty() || toStr.isEmpty()))
               || (!timesStr.isEmpty() && (!fromStr.isEmpty() || !toStr.isEmpty() || !incStr.isEmpty()))) {
            throw new IllegalArgumentException("Define either times or from, to, [inc]");
         }
         int from = 0, to = 1, inc = 1;
         if (!timesStr.isEmpty()) {
            to = parseRepeatArg(timesStr, "times", repeatName) - 1;
         } else {
            from = parseRepeatArg(fromStr, "from", repeatName);
            to = parseRepeatArg(toStr, "to", repeatName);
            if (!incStr.isEmpty()) {
               inc = parseRepeatArg(incStr, "inc", repeatName);           
            }
         }                  
         NodeList childNodes = element.getChildNodes();
         for (int counter = from; counter <= to; counter += inc) {
            System.getProperties().setProperty("repeat." + (repeatName.isEmpty() ? "counter" : repeatName + ".counter"), String.valueOf(counter));
            for (int i = 0; i < childNodes.getLength(); i++) {
               Node child = childNodes.item(i);
               if (child instanceof Element) {
                  addStage(prototype, (Element) child);            
               }
            }
         }         
      } else {
         Stage st = StageHelper.getStage(element.getNodeName());
         prototype.addStage(st);
         NamedNodeMap attributes = element.getAttributes();
         Map<String, String> attrToSet = new HashMap<String, String>();
         for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
            Attr attr = (Attr) attributes.item(attrIndex);
            attrToSet.put(attr.getName(), Evaluator.parseString(attr.getValue()));
         }
         PropertyHelper.setProperties(st, attrToSet, false);
         InitHelper.init(st);
      }
   }


   private int parseRepeatArg(String value, String name, String repeatName) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException e) {
         throw new IllegalArgumentException(String.format("Attribute %s=%s on %s is not an integer!", name, value, repeatName != null ? repeatName : "repeat"), e);
      }
   }
}
