package cn.neu.hotel.web;

import cn.neu.hotel.pojo.PageResult;
import cn.neu.hotel.pojo.RequestParams;
import cn.neu.hotel.service.IHotelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hotel")
public class HotelController {

    @Autowired
    private IHotelService hotelService;

    @PostMapping("/list")
    public PageResult search(@RequestBody RequestParams params){
        return hotelService.search(params);
    }

    @PostMapping("/filters")
    public Map<String, List<String>> getFilters(@RequestBody RequestParams params){return hotelService.filters(params);}

    @GetMapping("suggestion")
    public List<String> getSuggestion(@RequestParam("key") String key){
        return hotelService.getSuggestion(key);
    }
}
