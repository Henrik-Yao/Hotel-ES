package cn.neu.hotel.service.impl;

import cn.neu.hotel.mapper.HotelMapper;
import cn.neu.hotel.pojo.Hotel;
import cn.neu.hotel.pojo.HotelDoc;
import cn.neu.hotel.pojo.PageResult;
import cn.neu.hotel.pojo.RequestParams;
import cn.neu.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {

    @Autowired
    private RestHighLevelClient client;

    @Override
    public PageResult search(RequestParams params) {
        try {
            // 1.??????Request??????
            SearchRequest request = new SearchRequest("hotel");
            // 2.?????????????????????DSL??????
            // ??????boolQuery
            buildBasicQuery(params, request);

            // 2.2.??????
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);

            // 2.3.??????
            String location = params.getLocation();
            if(location != null && !location.equals("")){
                request.source().sort(SortBuilders
                        .geoDistanceSort("location", new GeoPoint(location))
                        .order(SortOrder.ASC)
                        .unit(DistanceUnit.KILOMETERS)
                );
            }

            // 3.????????????
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            return handleResponse(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void buildBasicQuery(RequestParams params, SearchRequest request) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // ???????????????
        String key = params.getKey();
        if(key == null || "".equals(key)){
            boolQuery.must(QueryBuilders.matchAllQuery());
        }else {
            boolQuery.must(QueryBuilders.matchQuery("all", key));
        }
        // ????????????
        if(params.getCity() != null && !params.getCity().equals("")){
            boolQuery.filter(QueryBuilders.termQuery("city", params.getCity()));
        }
        if(params.getBrand() != null && !params.getBrand().equals("")){
            boolQuery.filter(QueryBuilders.termQuery("brand", params.getBrand()));
        }
        if(params.getStarName() != null && !params.getStarName().equals("")){
            boolQuery.filter(QueryBuilders.termQuery("starName", params.getStarName()));
        }
        if(params.getMinPrice() != null && params.getMaxPrice() != null){
            boolQuery.filter(QueryBuilders
                    .rangeQuery("price")
                    .gte(params.getMinPrice())
                    .lte(params.getMaxPrice()));
        }

        // ????????????
        FunctionScoreQueryBuilder functionScoreQuery =
                QueryBuilders.functionScoreQuery(
                        boolQuery,
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        QueryBuilders.termQuery("isAD", true),
                                        ScoreFunctionBuilders.weightFactorFunction(10)
                                )
                        });

        request.source().query(functionScoreQuery);
    }

    @Override
    public Map<String, List<String>> filters(RequestParams params) {
        try {
            // 1.??????Request
            SearchRequest request = new SearchRequest("hotel");
            // 2.??????DSL
            buildBasicQuery(params, request);
            request.source().size(0);
            buildAggregation(request);
            // 3.????????????
            SearchResponse response = null;
            response = client.search(request, RequestOptions.DEFAULT);
            // 4.????????????
            Map<String, List<String>> result = new HashMap<>();
            Aggregations aggregations = response.getAggregations();
            // ???????????????????????????????????????
            List<String> brandList = getAggByName(aggregations, "brandAgg");
            result.put("brand", brandList);
            List<String> cityList = getAggByName(aggregations, "cityAgg");
            result.put("city", cityList);
            List<String> starList = getAggByName(aggregations, "starAgg");
            result.put("starName", starList);
            return result;
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    @Override
    public List<String> getSuggestion(String key) {
        try {
            // 1.??????Request
            SearchRequest request = new SearchRequest("hotel");
            // 2.??????DSL
            request.source().suggest(new SuggestBuilder().addSuggestion(
                    "suggestion",
                    SuggestBuilders.completionSuggestion("suggestion")
                            .prefix(key)
                            .skipDuplicates(true)
                            .size(10)
            ));
            // 3.????????????
            SearchResponse response = null;
            response = client.search(request, RequestOptions.DEFAULT);
            // 4.????????????
            Suggest suggest = response.getSuggest();
            // 4.1.?????????????????????????????????????????????
            CompletionSuggestion suggestion = suggest.getSuggestion("suggestion");
            // 4.2.??????options
            List<CompletionSuggestion.Entry.Option> options = suggestion.getOptions();
            // 4.3.??????
            List<String> list = new ArrayList<>(options.size());
            for(CompletionSuggestion.Entry.Option option : options){
                String text = option.getText().toString();
                list.add(text);
            }
            return list;
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }

    }

    @Override
    public void insertById(Long id) {
        try {
            //??????id??????????????????
            Hotel hotel = getById(id);
            HotelDoc hotelDoc = new HotelDoc(hotel);
            // 1.??????Request??????
            IndexRequest request = new IndexRequest("hotel").id(hotel.getId().toString());
            // 2.?????????????????????DSL??????
            request.source(JSON.toJSONString(hotelDoc), XContentType.JSON);
            // 3.????????????
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }

    }

    @Override
    public void deleteById(Long id) {
        try {
            // 1.??????request
            DeleteRequest request = new DeleteRequest("hotel", id.toString());
            // 2.????????????
            client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }

    private void buildAggregation(SearchRequest request) {
        request.source().aggregation(AggregationBuilders
                .terms("brandAgg")
                .field("brand")
                .size(100)
        );
        request.source().aggregation(AggregationBuilders
                .terms("cityAgg")
                .field("city")
                .size(100)
        );
        request.source().aggregation(AggregationBuilders
                .terms("starAgg")
                .field("starName")
                .size(100)
        );
    }

    private List<String> getAggByName(Aggregations aggregations, String aggName) {
        // 4.1.????????????????????????????????????
        Terms brandTerms = aggregations.get(aggName);
        // 4.2.??????buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 4.3.??????
        List<String> brandList = new ArrayList<>();
        for (Terms.Bucket bucket : buckets){
            // 4.4.??????key
            String key = bucket.getKeyAsString();
            brandList.add(key);
        }
        return brandList;
    }

    private PageResult handleResponse(SearchResponse response) {
        // 4.????????????
        SearchHits searchHits = response.getHits();
        // 4.1.???????????????
        long total = searchHits.getTotalHits().value;

        List<HotelDoc> hotels = new ArrayList<>();

        // 4.1.????????????
        SearchHit[] hits = searchHits.getHits();
        // 4.3.??????
        for (SearchHit hit : hits) {
            // ????????????source
            String json = hit.getSourceAsString();
            // ????????????
            HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);
            // ???????????????
            Object[] sortValues = hit.getSortValues();
            if(sortValues.length > 0){
                Object sortValue = sortValues[0];
                hotelDoc.setDistance(sortValue);
            }
            hotels.add(hotelDoc);
        }
        return new PageResult(total, hotels);
    }
}
