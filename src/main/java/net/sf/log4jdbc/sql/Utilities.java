/**
 * Copyright 2007-2012 Arthur Blake
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.log4jdbc.sql;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

import java.util.HashMap;
import java.util.Map;

/**
 * Static utility methods for use throughout the project.
 */
public class Utilities {
  /**
   * Right justify a field within a certain number of spaces.
   * @param fieldSize field size to right justify field within.
   * @param field contents to right justify within field.
   * @return the field, right justified within the requested size.
   */
  public static String rightJustify(int fieldSize, String field)
  {
    if (field==null)
    {
      field="";
    }
    StringBuffer output = new StringBuffer();
    for (int i=0, j = fieldSize-field.length(); i < j; i++)
    {
      output.append(' ');
    }
    output.append(field);
    return output.toString();
  }

  public static Span startSpan(String operationName, String sql){
     Span span = GlobalTracer.get().buildSpan(QueryMethodTableSpanName.INSTANCE.querySpanName(sql))
            .withTag(Tags.COMPONENT, "java-jdbc")
            .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.DB_TYPE, "sql")
            .start();

     if (sql.length() < 50000){
       span.setTag(Tags.DB_STATEMENT, sql);
     }else {
       span.setTag(Tags.DB_STATEMENT, sql.substring(0, 50000));
     }
     return span;
  }

  public static void finishSpan(Span span, Exception e){
    Map<String, Object> log = new HashMap<String, Object>();
    span.setTag(Tags.ERROR, true);
    log.put(Fields.ERROR_OBJECT, span);
    span.log(log);
    span.finish();
  }
}
