/*
 * Copyright 2016 org.NLP4L
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nlp4l.solr.ltr;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SyntaxError;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class LinearWeightQParserPlugin extends QParserPlugin {
  List<FieldFeatureExtractorFactory> featuresSpec = new ArrayList<FieldFeatureExtractorFactory>();
  List<Float> weights = new ArrayList<Float>();

  @Override
  public void init(NamedList args){
    NamedList settings = (NamedList)args.get("settings");
    String featuresFileName = (String)settings.get("features");
    String modelFileName = (String)settings.get("model");

    FeaturesConfigReader fcReader = null;
    try {
      fcReader = new FeaturesConfigReader(featuresFileName);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      LinearWeightModelReader mcReader = new LinearWeightModelReader(modelFileName);

      LinearWeightModelReader.WeightDesc[] weightDescs = mcReader.getWeightDescs();

      for(LinearWeightModelReader.WeightDesc weightDesc: weightDescs){
        weights.add(weightDesc.weight);
        FeaturesConfigReader.FeatureDesc featureDesc = fcReader.getFeatureDesc(weightDesc.name);
        if(featureDesc == null){
          throw new IllegalArgumentException("no such feature " + weightDesc.name + " in the feature conf file");
        }
        FieldFeatureExtractorFactory dfeFactory = FeaturesConfigReader.loadFactory(featureDesc);
        featuresSpec.add(dfeFactory);
      }
    } catch (IOException ignored) {
      // TODO: log warning...
    }
  }

  @Override
  public QParser createParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new LinearWeightQParser(query, localParams, params, req);
  }

  public class LinearWeightQParser extends QParser {

    public LinearWeightQParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
      super(query, localParams, params, req);
    }

    @Override
    public Query parse() throws SyntaxError {
      IndexReaderContext context = req.getSearcher().getTopReaderContext();
      for(FieldFeatureExtractorFactory factory: featuresSpec){
        String fieldName = factory.getFieldName();
        FieldType fieldType = req.getSchema().getFieldType(fieldName);
        Analyzer analyzer = fieldType.getQueryAnalyzer();
        factory.init(context, FieldFeatureExtractorFactory.terms(fieldName, qstr, analyzer));
      }

      return new LinearWeightQuery(featuresSpec, weights);
    }
  }
}
