package cn.neu.hotel;

import cn.neu.hotel.service.IHotelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class HotelDemoApplicationTests {

    @Autowired
    private IHotelService hotelService;

    @Test
    void contextLoads() {
//        Map<String, List<String>> filters = hotelService.filters();
//
//        System.out.println(filters);
    }

}