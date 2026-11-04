package com.box.reservation.ai.resolve;

import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.reservation.entity.CoachResolveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

//@Component
//@RequiredArgsConstructor
//public class CoachNameResolver {
//
//    private final CoachFeignClient coachFeignClient;
//
//    public CoachResolveResult resolve(String coachName) {
//        if (coachName == null || coachName.isBlank()) {
//            return new CoachResolveResult(CoachResolveResult.Status.NOT_FOUND, null, null);
//        }
//
//        // 2. 拿全量名单
//        List<CoachDTO> all = coachFeignClient.getAll().getData();
//
//        // 3. 剥称呼 -> 核心关键词（"唐教练" -> "唐"，"老张" -> "张"）
//        String key = stripPrefix(coachName);
//        key = stripSuffix(key);
//        // 4. 精确匹配：name.equals(key) 命中就是唯一
//        for (CoachDTO coach : all){
//            if (coach.getName().equals(key)) {
//                return result.coach(coach);
//            }
//        }

        // 5. 精确没中，退一步 contains：name.contains(key)
        //    "唐" 命中 [唐三, 唐伟]
        // TODO 5

        // 6. 按数量分支
        //    size == 1 -> resolved
        //    size > 1  -> ambiguous(candidates 里的 name 列表)
        //    size == 0 -> notFound
        // TODO 6
//    }
//
//    private String stripPrefix(String s) {
//        if (s.startsWith("老")) return s.substring(1);   // 老张 -> 张
//        if (s.startsWith("小")) return s.substring(1);   // 小王 -> 王
//        return s;                                         // 没命中，原样返回
//    }
//
//    private String stripSuffix(String s) {
//        if (s.endsWith("教练")) return s.substring(0, s.length() - 1);   // 张教练 -> 张
//        if (s.endsWith("老师")) return s.substring(0, s.length() - 1);   // 王老师 -> 王
//        return s;                                         // 没命中，原样返回
//    }
//}
