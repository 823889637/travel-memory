package com.travelmemory.service;
import com.travelmemory.dto.CreateUserRequest;
import com.travelmemory.dto.RegisterRequest;
import com.travelmemory.dto.RegistrationStatusResponse;
import com.travelmemory.dto.UserResponse;
import com.travelmemory.security.UserPrincipal;
import java.util.List;
public interface AppUserService {
 UserPrincipal authenticate(String username,String password);
 RegistrationStatusResponse registrationStatus();
 UserPrincipal register(RegisterRequest request);
 UserPrincipal currentPrincipal(Long id);
 void changePassword(Long userId,String currentPassword,String newPassword);
 List<UserResponse> listUsers();
 UserResponse createUser(CreateUserRequest request);
 UserResponse setEnabled(Long actorId,Long userId,boolean enabled);
 UserResponse resetPassword(Long userId,String temporaryPassword);
 void bootstrapInitialAdmin(String username,String password);
 UserPrincipal updateAvatar(Long userId,String avatarUrl);
}
