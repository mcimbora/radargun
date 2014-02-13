package org.radargun.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import org.radargun.utils.Tokenizer;

/**
 * @author Radim Vansa
 */
public class Evaluator {

   public static int parseInt(String string) {
      return Integer.parseInt(parseString(string));
   }

   public static String parseString(String string) {
      if (string == null) return null;
      StringBuilder sb = new StringBuilder();
      int currentIndex = 0;
      while (currentIndex < string.length()) {
         int propertyIndex = string.indexOf("${", currentIndex);
         int expressionIndex = string.indexOf("#{", currentIndex);
         int nextIndex = propertyIndex < 0 ?
               (expressionIndex < 0 ? string.length() : expressionIndex) :
               (expressionIndex < 0 ? propertyIndex : Math.min(expressionIndex, propertyIndex));
         sb.append(string.substring(currentIndex, nextIndex));
         currentIndex = nextIndex + 2;
         if (nextIndex == propertyIndex) {
            nextIndex = string.indexOf('}', currentIndex);
            if (nextIndex < 0) {
               throw new IllegalArgumentException(string);
            }
            sb.append(evalProperty(string, currentIndex, nextIndex));
            currentIndex = nextIndex + 1;
         } else if (nextIndex == expressionIndex) {
            Stack<Operator> operators = new Stack<Operator>();
            Stack<String> operands = new Stack<String>();
            Tokenizer tokenizer = new Tokenizer(string, Operator.symbols(), true, false, currentIndex);
            boolean closed = false;
            while (tokenizer.hasMoreTokens()) {
               String token = tokenizer.nextToken();
               Operator op = Operator.from(token);
               if (op == null) {
                  operands.push(token);
               } else if (op.isWhite()) {
                  continue;
               } else if (op == Operator.OPENVAR) {
                  if (!tokenizer.hasMoreTokens()) throw new IllegalArgumentException(string);
                  StringBuilder var = new StringBuilder();
                  while (tokenizer.hasMoreTokens()) {
                     token = tokenizer.nextToken();
                     if ((op = Operator.from(token)) == null || op.isWhite()) {
                        var.append(token);
                     } else {
                        break;
                     }
                  }
                  if (op != Operator.CLOSEVAR) {
                     throw new IllegalArgumentException("Expected '}' but found " + token + " in " + string);
                  }
                  operands.push(evalProperty(var.toString(), 0, var.length()).trim());
               } else if (op == Operator.CLOSEVAR) {
                  // end of expression to be evaluated
                  closed = true;
                  break;
               } else if (op == Operator.OPENPAR) {
                  operators.push(op);
               } else if (op == Operator.CLOSEPAR) {
                  while ((op = operators.pop()) != Operator.OPENPAR) {
                      String second = operands.pop();
                      String first = operands.pop();
                      operands.push(op.exec(first, second));
                  }
               } else {
                  while (true) {
                     if ((operators.empty()) || (operators.peek() == Operator.OPENPAR) ||
                         (operators.peek().precedence() < op.precedence())) {
                        operators.push(op);
                        break;
                     }
                     Operator last = operators.pop();
                     String second = operands.pop();
                     String first = operands.pop();
                     operands.push(last.exec(first, second));
                  }
               }
            }
            if (!closed) {
               throw new IllegalArgumentException("Expression is missing closing '}': " + string);
            }
            while (!operators.empty()) {
               Operator last = operators.pop();
               String second = operands.pop();
               String first = operands.pop();
               operands.push(last.exec(first, second));
            }
            sb.append(operands.pop());
            if (!operands.empty()) {
               throw new IllegalArgumentException(operands.size() + " operands not processed: top=" + operands.pop() + " all=" + operands);
            }
            currentIndex = tokenizer.getPosition();
         }
      }
      return sb.toString();
   }


   private static String evalProperty(String string, int startIndex, int endIndex) {
      int colonIndex = string.indexOf(':', startIndex);
      String property, value;
      if (colonIndex < 0) {
         property = string.substring(startIndex, endIndex).trim();
         value = System.getProperty(property);
      } else {
         property = string.substring(startIndex, colonIndex).trim();
         String def = string.substring(colonIndex + 1, endIndex);
         value = System.getProperty(property, def);
      }
      if (value == null && property.startsWith("random.")) {
         return random(property);
      } else if (value != null) {
         return value;
      } else {
         throw new IllegalArgumentException("Property " + property + " not defined!");
      }
   }

   private static String random(String type) {
      Random random = new Random();
      if (type.equals("random.int")) {
         return String.valueOf(Math.abs(random.nextInt()));
      } else if (type.equals("random.long")) {
         return String.valueOf(Math.abs(random.nextLong()));
      } else if (type.equals("random.double")) {
         return String.valueOf(random.nextDouble());
      } else if (type.equals("random.boolean")) {
         return String.valueOf(random.nextBoolean());
      } else {
         throw new IllegalArgumentException("Unknown random type: " + type);
      }
   }

   private static String range(String first, String second) {
      try {
         int from = Integer.parseInt(first);
         int to = Integer.parseInt(second);
         if (from > to) {
            int temp = from;
            from = to;
            to = temp;
         } else if (from == to) return String.valueOf(from);
         StringBuilder sb = new StringBuilder(String.valueOf(from));
         for (int i = from + 1; i <= to; ++i) sb.append(',').append(i);
         return sb.toString();
      }  catch (NumberFormatException e2) {
         throw new IllegalArgumentException(first + " .. " + second);
      }
   }

   private static String multiply(String first, String second) {
      try {
         return String.valueOf(Integer.parseInt(first) * Integer.parseInt(second));
      } catch (NumberFormatException e) {
         try {
            return String.valueOf(Double.parseDouble(first) * Double.parseDouble(second));
         } catch (NumberFormatException e2) {
            throw new IllegalArgumentException(first + " * " + second);
         }
      }
   }

   private static String minus(String first, String second) {
      try {
         return String.valueOf(Integer.parseInt(first) - Integer.parseInt(second));
      } catch (NumberFormatException e) {
         try {
            return String.valueOf(Double.parseDouble(first) - Double.parseDouble(second));
         } catch (NumberFormatException e2) {
            throw new IllegalArgumentException(first + " - " + second);
         }
      }
   }

   private static String plus(String first, String second) {
      try {
         return String.valueOf(Integer.parseInt(first) + Integer.parseInt(second));
      } catch (NumberFormatException e) {
         try {
            return String.valueOf(Double.parseDouble(first) + Double.parseDouble(second));
         } catch (NumberFormatException e2) {
            throw new IllegalArgumentException(first + " + " + second);
         }
      }
   }

   private static String div(String first, String second) {
      try {
         return String.valueOf(Integer.parseInt(first) / Integer.parseInt(second));
      } catch (NumberFormatException e) {
         try {
            return String.valueOf(Double.parseDouble(first) / Double.parseDouble(second));
         } catch (NumberFormatException e2) {
            throw new IllegalArgumentException(first + " / " + second);
         }
      }
   }

   private static String modulo(String first, String second) {
      try {
         return String.valueOf(Integer.parseInt(first) % Integer.parseInt(second));
      } catch (NumberFormatException e) {
         throw new IllegalArgumentException(first + " % " + second);
      }
   }

   private static interface TwoArgFunctor {
      String exec(String first, String second);
   }

   public enum Operator {
      SPACE(" ", 0, true, null),
      TAB("\t", 0, true, null),
      NEWLINE("\n", 0, true, null),
      CR("\n", 0, true, null),
      PLUS("+", 100, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return plus(first, second);
         }
      }),
      MINUS("-", 100, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return minus(first, second);
         }
      }),
      MULTIPLY("*", 200, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return multiply(first, second);
         }
      }),
      DIVIDE("/", 200, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return div(first, second);
         }
      }),
      MODULO("%", 200, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return modulo(first, second);
         }
      }),
      RANGE("..", 50, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return range(first, second);
         }
      }),
      COMMA(",", 10, false, new TwoArgFunctor() {
         @Override
         public String exec(String first, String second) {
            return first + "," + second;
         }
      }),
      OPENPAR("(", 0, false, null),
      CLOSEPAR(")", 0, false, null),
      OPENVAR("${", 0, false, null),
      CLOSEVAR("}", 0, false, null)
      ;

      private static Map<String, Operator> symbolMap = new HashMap<String, Operator>();
      private String symbol;
      private int precedence;
      private boolean isWhite;
      private TwoArgFunctor functor;

      static {
         for (Operator op : values()) {
            symbolMap.put(op.symbol, op);
         }
      }

      Operator(String symbol, int precedence, boolean isWhite, TwoArgFunctor functor) {
         this.symbol = symbol;
         this.precedence = precedence;
         this.functor = functor;
         this.isWhite = isWhite;
      }

      public static String[] symbols() {
         Operator[] values = values();
         String[] symbols = new String[values.length];
         for (int i = 0; i < values.length; ++i) {
            symbols[i] = values[i].symbol;
         }
         return symbols;
      }

      public static Operator from(String symbol) {
         return symbolMap.get(symbol);
      }

      public String exec(String first, String second) {
         return functor.exec(first, second);
      }

      public int precedence() {
         return precedence;
      }

      public boolean isWhite() {
         return isWhite;
      }
   }
}
