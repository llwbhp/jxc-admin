package cn.toesbieya.jxc.statistic.controller;

import cn.toesbieya.jxc.common.model.vo.Result;
import cn.toesbieya.jxc.statistic.service.StatisticService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("statistic")
public class StatisticController {
    @Resource
    private StatisticService statisticService;

    @GetMapping("getFourBlock")
    public Result getFourBlock() {
        return Result.success(statisticService.getFourBlock());
    }

    @GetMapping("getDailyProfitStat")
    public Result getDailyProfitStat() {
        return Result.success(statisticService.getDailyProfitStat());
    }

    @GetMapping("getDailyFinishOrder")
    public Result getDailyFinishOrder() {
        return Result.success(statisticService.getDailyFinishOrder());
    }

    @GetMapping("getTotalProfitGoods")
    public Result getTotalProfitGoods() {
        return Result.success(statisticService.getTotalProfitGoods());
    }
}
