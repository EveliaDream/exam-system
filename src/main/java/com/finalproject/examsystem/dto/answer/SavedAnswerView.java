package com.finalproject.examsystem.dto.answer;

import java.util.List;

/** 已保存的作答（用于进入考试/断线重进时回填）。 */
public record SavedAnswerView(Long questionId, List<String> answer) {
}
