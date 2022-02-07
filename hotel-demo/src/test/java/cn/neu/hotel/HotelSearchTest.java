package cn.neu.hotel;

import cn.neu.hotel.pojo.HotelDoc;
import com.alibaba.fastjson.JSON;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.neu.hotel.constants.HotelIndexConstants.MAPPING_TEMPLATE;

public class HotelSearchTest {
    private RestHighLevelClient client;

    @Test
    void testMatchAll() throws IOException {
        // 1.创建Request对象
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备请求参数；DSL语句
        request.source().query(QueryBuilders.matchAllQuery());
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testMatch() throws IOException {
        // 1.创建Request对象
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备请求参数；DSL语句
        request.source()
                .query(QueryBuilders.matchQuery("all", "如家"));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);

    }

    @Test
    void testBool() throws IOException {
        // 1.创建Request对象
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备请求参数；DSL语句
        // 2.1.准备BooleanQuery
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 2.2.添加term
        boolQuery.must(QueryBuilders.termQuery("city","上海"));
        // 2.3.添加range
        boolQuery.filter(QueryBuilders.rangeQuery("price").gte(100));
        request.source().query(boolQuery);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);
    }

    @Test
    void testPageAndSort() throws IOException {
        // 页码，每页大小
        int page = 2, size = 5;
        // 1.创建Request对象
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备请求参数；DSL语句
        // 2.1.query
        request.source().query(QueryBuilders.matchAllQuery());
        // 2.2.sort
        request.source().sort("price", SortOrder.ASC);
        // 2.3.分页
        request.source().from((page-1)*size).size(size);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);
    }

    @Test
    void testHighlight() throws IOException {
        // 1.创建Request对象
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备请求参数；DSL语句
        // 2.1.query
        request.source().query(QueryBuilders.matchQuery("all", "如家"));
        // 2.2.高亮
        request.source().highlighter(new HighlightBuilder().field("name").requireFieldMatch(false));
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);
    }



    private void handleResponse(SearchResponse response) {
        // 4.解析响应
        SearchHits searchHits = response.getHits();
        // 4.1.获得总条数
        long total = searchHits.getTotalHits().value;
        System.out.println(total);
        // 4.1.文档数组
        SearchHit[] hits = searchHits.getHits();
        // 4.3.遍历
        for (SearchHit hit : hits) {
            // 获取文档source
            String json = hit.getSourceAsString();
            // 反序列化
            HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);
            // 获取高亮结果
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if(!CollectionUtils.isEmpty(highlightFields)){
                // 根据字段名获取高亮结果
                HighlightField highlightField = highlightFields.get("name");
                // 获取高亮值
                String name = highlightField.getFragments()[0].string();
                // 覆盖非高亮结果
                hotelDoc.setName(name);
            }
            // 覆盖非高亮值
            System.out.println(hotelDoc);
        }
    }

    @Test
    void testAggregation() throws IOException {
        // 1.准备Request
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备DSL
        request.source().size(0);
        request.source().aggregation(AggregationBuilders
                .terms("brandAgg")
                .field("brand")
                .size(10)
        );
        // 3.发出请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析结果
        Aggregations aggregations = response.getAggregations();
        // 4.1.根据聚合名称获取聚合结果
        Terms brandTerms = aggregations.get("brandAgg");
        // 4.2.获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 4.3.遍历
        for (Terms.Bucket bucket : buckets){
            // 4.4.获取key
            String key = bucket.getKeyAsString();
            System.out.println(key);
        }
    }

    @Test
    void testSuggestion() throws IOException {
        // 1.准备Request
        SearchRequest request = new SearchRequest("hotel");
        // 2.准备DSL
        request.source().suggest(new SuggestBuilder().addSuggestion(
                "suggestion",
                SuggestBuilders.completionSuggestion("suggestion")
                .prefix("h")
                .skipDuplicates(true)
                .size(10)
        ));
        // 3.发起请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析结果
        Suggest suggest = response.getSuggest();
        // 4.1.根据补全查询名称，获取补全结果
        CompletionSuggestion suggestion = suggest.getSuggestion("suggestion");
        // 4.2.获取options
        List<CompletionSuggestion.Entry.Option> options = suggestion.getOptions();
        for(CompletionSuggestion.Entry.Option option : options){
            String text = option.getText().toString();
            System.out.println(text);
        }
    }

    @BeforeEach
    void setUp(){
        //创建连接
        this.client = new RestHighLevelClient(RestClient.builder(HttpHost.create("http://101.43.16.42:9200")));
    }

    @AfterEach
    void tearDown() {
        try {
            //销毁连接
            this.client.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
