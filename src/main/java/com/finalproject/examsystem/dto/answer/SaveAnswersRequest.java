package com.finalproject.examsystem.dto.answer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 自动保存请求：一次提交学生本次修改过的若干题。
 * <p>
 * 采用「批量更新本次变更」而不是「全量覆盖」：
 * 学生答题过程中每次只改一两道题，全量提交会带来无谓的写入压力。
 * <p>
 * 耗时与修改次数由前端测量后上报，服务端只做累加与上限保护
 * （见 Service 中的时间校验），不信任但也不丢弃。
 */
public record SaveAnswersRequest(

        @NotNull(message = "答案列表不能为空")
        List<@Valid Item> answers) {

    public record Item(

            @NotNull(message = "题目 id 不能为空")
            Long questionId,

            /** 学生答案；单选题/判断题/填空题为单元素，多选题为多元素 */
            List<String> answer,

            /** 该题累计作答耗时（秒），前端用时间戳差值计算 */
            @Min(value = 0, message = "耗时不能为负")
            @Max(value = 86400, message = "单题耗时超出合理范围")
            Integer timeSpentSec,

            /** 该题被修改的次数 */
            @Min(value = 0, message = "修改次数不能为负")
            @Max(value = 10000, message = "修改次数超出合理范围")
            Integer submitCount) {
    }
}
