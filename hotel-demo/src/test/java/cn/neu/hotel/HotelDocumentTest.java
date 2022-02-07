package cn.neu.hotel;

import cn.neu.hotel.pojo.Hotel;
import cn.neu.hotel.pojo.HotelDoc;
import cn.neu.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

import static cn.neu.hotel.constants.HotelIndexConstants.MAPPING_TEMPLATE;

@SpringBootTest
public class HotelDocumentTest {
    @Autowired
    private IHotelService hotelService;

    private RestHighLevelClient client;

    @Test
    void testAddDocument() throws IOException {
        //根据id查询酒店数据
        Hotel hotel = hotelService.getById(61083L);
        HotelDoc hotelDoc = new HotelDoc(hotel);
        // 1.创建Request对象
        IndexRequest request = new IndexRequest("hotel").id(hotel.getId().toString());
        // 2.准备请求参数；DSL语句
        request.source(JSON.toJSONString(hotelDoc),XContentType.JSON);
        // 3.发送请求
        client.index(request, RequestOptions.DEFAULT);
    }

    @Test
    void testGetDocumentById() throws IOException {
        // 1.创建Request对象
        GetRequest request = new GetRequest("hotel", "61083");
        // 2.发送请求
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        // 3.解析响应结果
        String json = response.getSourceAsString();
        //反序列化
        HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);
        System.out.println(hotelDoc);
    }

    @Test
    void testUpdateDocument() throws IOException {
        // 1.准备request
        UpdateRequest request = new UpdateRequest("hotel", "61083");
        // 2.准备请求参数
        request.doc(
                "price","952",
                "starName","四钻"
        );
        // 3.发送请求
        client.update(request, RequestOptions.DEFAULT);
    }


    @Test
    void testDeleteDocument() throws IOException {
        // 1.准备request
        DeleteRequest request = new DeleteRequest("hotel", "61083");
        // 2.发送请求
        client.delete(request, RequestOptions.DEFAULT);
    }


    @Test
    void testBulkDocument() throws IOException {
        //批量查询酒店数据
        List<Hotel> hotels = hotelService.list();
        // 1.创建Request对象
        BulkRequest request = new BulkRequest();
        // 2.准备请求参数，添加多个新增的Request
        for(Hotel hotel:hotels){
            // 转换为HotelDoc
            HotelDoc hotelDoc = new HotelDoc(hotel);
            request.add(new IndexRequest("hotel")
                    .id(hotel.getId().toString())
                    .source(JSON.toJSONString(hotelDoc),XContentType.JSON));
        }
        // 3.发送请求
        client.bulk(request, RequestOptions.DEFAULT);
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
