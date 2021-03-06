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

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractLinearWeightScorer extends Scorer {

  protected final List<FieldFeatureExtractor[]> featuresSpec;
  protected final List<Float> weights;
  protected final DocIdSetIterator iterator;

  public AbstractLinearWeightScorer(Weight luceneWeight, List<FieldFeatureExtractor[]> featuresSpec,
                           List<Float> weights, DocIdSetIterator iterator){
    super(luceneWeight);
    this.featuresSpec = featuresSpec;
    this.weights = weights;
    this.iterator = iterator;
  }

  public float innerProduct() throws IOException {
    final int target = docID();
    float value = 0;
    int idx = 0;
    for(FieldFeatureExtractor[] extractors: featuresSpec){
      float feature = 0;
      for(FieldFeatureExtractor extractor: extractors){
        feature += extractor.feature(target);
      }

      value += weights.get(idx) * feature;
      idx++;
    }

    return value;
  }

  public List<Explanation> subExplanations(int target) throws IOException {
    List<Explanation> expls = new ArrayList<Explanation>();
    int idx = 0;
    for(FieldFeatureExtractor[] extractors: featuresSpec){
      float feature = 0;
      List<Explanation> subExpls = new ArrayList<Explanation>();
      for(FieldFeatureExtractor extractor: extractors){
        feature += extractor.feature(target);
        subExpls.add(extractor.explain(target));
      }
      float w = weights.get(idx);
      float score = w * feature;
      expls.add(Explanation.match(score, "weight: " + w + " * feature: " + feature + " sum of:", subExpls));
      idx++;
    }
    return expls;
  }

  @Override
  public int docID() {
    return iterator.docID();
  }

  @Override
  public DocIdSetIterator iterator() {
    return iterator;
  }
}
