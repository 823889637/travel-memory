package com.travelmemory.controller;
import com.travelmemory.common.Result;import com.travelmemory.dto.*;import com.travelmemory.security.CurrentUser;import com.travelmemory.service.AppUserService;import jakarta.validation.Valid;import java.util.List;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/users") public class AdminUserController{private final AppUserService users;private final CurrentUser current;public AdminUserController(AppUserService users,CurrentUser current){this.users=users;this.current=current;}
 @GetMapping public Result<List<UserResponse>> list(){return Result.success(users.listUsers());}
 @PostMapping public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest request){return Result.success(users.createUser(request));}
 @PutMapping("/{id}/enabled") public Result<UserResponse> enabled(@PathVariable Long id,@Valid @RequestBody UserEnabledRequest request){if(request.getEnabled()==null)throw new com.travelmemory.exception.BusinessException(400,"enabled is required");return Result.success(users.setEnabled(current.requireId(),id,request.getEnabled()));}
 @PutMapping("/{id}/password") public Result<UserResponse> reset(@PathVariable Long id,@Valid @RequestBody AdminPasswordResetRequest request){return Result.success(users.resetPassword(id,request.getTemporaryPassword()));}}
