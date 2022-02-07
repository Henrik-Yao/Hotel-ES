package cn.neu.hotel.service;

import cn.neu.hotel.pojo.Hotel;
import cn.neu.hotel.pojo.PageResult;
import cn.neu.hotel.pojo.RequestParams;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface IHotelService extends IService<Hotel> {
    PageResult search(RequestParams params);

    Map<String, List<String>> filters(RequestParams params);

    List<String> getSuggestion(String key);

    void insertById(Long id);

    void deleteById(Long id);
}
