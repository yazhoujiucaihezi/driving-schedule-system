package com.box.student.controller;

import com.box.api.student.StudentDTO;
import com.box.common.result.Result;
import com.box.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/student")
public class StudentInternalController {

    private final StudentService studentService;
    @GetMapping("/by-id/{id}")
    public Result<StudentDTO> getById(@PathVariable("id") Long id) {
        return Result.success(studentService.getDTObyId(id));
    }

    @GetMapping("/by-username/{username}")
    public Result<List<StudentDTO>> getByUsername(@PathVariable("username") String username) {
        return Result.success(studentService.getDTOByUsername(username));
    }
}
