package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmemory.dto.CreateUserRequest;
import com.travelmemory.dto.RegisterRequest;
import com.travelmemory.dto.RegistrationStatusResponse;
import com.travelmemory.dto.UserResponse;
import com.travelmemory.entity.AppUser;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.AppUserMapper;
import com.travelmemory.security.UserPrincipal;
import com.travelmemory.service.AppUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserServiceImpl implements AppUserService {
 private static final int MAX_FAILURES=5; private static final int LOCK_MINUTES=15;
 private final AppUserMapper mapper; private final PasswordEncoder encoder;
 @Value("${app.registration.enabled:false}") private boolean registrationEnabled;
 @Value("${app.registration.invite-code:}") private String registrationInviteCode;
 public AppUserServiceImpl(AppUserMapper mapper,PasswordEncoder encoder){this.mapper=mapper;this.encoder=encoder;}
 @Override @Transactional public UserPrincipal authenticate(String username,String password){
  AppUser user=findByUsername(normalizeUsername(username),true);
  if(user==null||!Boolean.TRUE.equals(user.getEnabled())||isLocked(user)||!encoder.matches(password,user.getPasswordHash())){if(user!=null)recordFailure(user);throw new BusinessException(401,"用户名或密码错误");}
  user.setFailedLoginCount(0);user.setLockedUntil(null);user.setLastLoginTime(LocalDateTime.now());mapper.updateById(user);return new UserPrincipal(user);
 }
 @Override public RegistrationStatusResponse registrationStatus(){AppUser initial=mapper.selectById(1L);return new RegistrationStatusResponse(isRegistrationAvailable(),isReservedInitialAdmin(initial));}
 @Override @Transactional public UserPrincipal register(RegisterRequest request){
  if(!isRegistrationAvailable())throw new BusinessException(403,"注册暂未开放");validateInvitationCode(request.getInvitationCode());
  String username=normalizeUsername(request.getUsername());String displayName=normalizeDisplayName(request.getDisplayName());validatePassword(request.getPassword());
  AppUser initial=mapper.selectByIdForUpdate(1L);
  if(findByUsername(username,false)!=null)throw new BusinessException(400,"用户名不可用");
  if(isReservedInitialAdmin(initial)){activateInitialAdmin(initial,username,displayName,request.getPassword(),false);return new UserPrincipal(initial);}
  AppUser user=new AppUser();user.setUsername(username);user.setDisplayName(displayName);user.setPasswordHash(encoder.encode(request.getPassword()));user.setRole("USER");user.setEnabled(true);user.setMustChangePassword(false);user.setFailedLoginCount(0);
  try{mapper.insert(user);}catch(DuplicateKeyException exception){throw new BusinessException(400,"用户名不可用");}return new UserPrincipal(user);
 }
 @Override public UserPrincipal currentPrincipal(Long id){AppUser user=mapper.selectById(id);if(user==null||!Boolean.TRUE.equals(user.getEnabled()))throw new BusinessException(401,"Authentication is required");return new UserPrincipal(user);}
 @Override @Transactional public void changePassword(Long userId,String currentPassword,String newPassword){AppUser user=requireUser(userId);if(!encoder.matches(currentPassword,user.getPasswordHash()))throw new BusinessException(400,"当前密码不正确");validatePassword(newPassword);user.setPasswordHash(encoder.encode(newPassword));user.setMustChangePassword(false);user.setFailedLoginCount(0);user.setLockedUntil(null);mapper.updateById(user);}
 @Override public List<UserResponse> listUsers(){return mapper.selectList(new LambdaQueryWrapper<AppUser>().isNotNull(AppUser::getUsername).orderByAsc(AppUser::getCreateTime)).stream().map(UserResponse::from).toList();}
 @Override @Transactional public UserResponse createUser(CreateUserRequest request){String username=normalizeUsername(request.getUsername());if(findByUsername(username,false)!=null)throw new BusinessException(400,"用户名已存在");validatePassword(request.getTemporaryPassword());AppUser user=new AppUser();user.setUsername(username);user.setDisplayName(normalizeDisplayName(request.getDisplayName()));user.setPasswordHash(encoder.encode(request.getTemporaryPassword()));user.setRole("USER");user.setEnabled(true);user.setMustChangePassword(true);user.setFailedLoginCount(0);mapper.insert(user);return UserResponse.from(user);}
 @Override @Transactional public UserResponse setEnabled(Long actorId,Long userId,boolean enabled){AppUser user=requireUser(userId);if(actorId.equals(userId)&&!enabled)throw new BusinessException(400,"不能禁用当前账号");if("ADMIN".equals(user.getRole())&&!enabled&&enabledAdminCount()<=1)throw new BusinessException(400,"至少保留一个可用管理员");user.setEnabled(enabled);if(!enabled){user.setLockedUntil(null);user.setFailedLoginCount(0);}mapper.updateById(user);return UserResponse.from(user);}
 @Override @Transactional public UserResponse resetPassword(Long userId,String temporaryPassword){AppUser user=requireUser(userId);validatePassword(temporaryPassword);user.setPasswordHash(encoder.encode(temporaryPassword));user.setMustChangePassword(true);user.setFailedLoginCount(0);user.setLockedUntil(null);mapper.updateById(user);return UserResponse.from(user);}
 @Override @Transactional public void bootstrapInitialAdmin(String username,String password){AppUser user=mapper.selectByIdForUpdate(1L);if(!isReservedInitialAdmin(user))throw new BusinessException(400,"初始管理员已经激活或初始化状态无效");String normalizedUsername=normalizeUsername(username);if(findByUsername(normalizedUsername,false)!=null)throw new BusinessException(400,"用户名已存在");validatePassword(password);activateInitialAdmin(user,normalizedUsername,"Administrator",password,true);}
 private void activateInitialAdmin(AppUser user,String username,String displayName,String password,boolean mustChangePassword){user.setUsername(username);user.setDisplayName(displayName);user.setPasswordHash(encoder.encode(password));user.setRole("ADMIN");user.setEnabled(true);user.setMustChangePassword(mustChangePassword);user.setFailedLoginCount(0);user.setLockedUntil(null);mapper.updateById(user);}
 private boolean isReservedInitialAdmin(AppUser user){return user!=null&&user.getUsername()==null&&!Boolean.TRUE.equals(user.getEnabled())&&"!".equals(user.getPasswordHash())&&"ADMIN".equals(user.getRole());}
 private boolean isRegistrationAvailable(){return registrationEnabled&&registrationInviteCode!=null&&!registrationInviteCode.isBlank();}
 private void validateInvitationCode(String invitationCode){if(invitationCode==null||!MessageDigest.isEqual(registrationInviteCode.getBytes(StandardCharsets.UTF_8),invitationCode.getBytes(StandardCharsets.UTF_8)))throw new BusinessException(403,"注册码无效");}
 private AppUser findByUsername(String username,boolean locked){if(username==null)return null;LambdaQueryWrapper<AppUser> q=new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername,username);if(locked)q.last("FOR UPDATE");return mapper.selectOne(q);}
 private AppUser requireUser(Long id){AppUser user=mapper.selectById(id);if(user==null||user.getUsername()==null)throw new BusinessException(404,"用户不存在");return user;}
 private void recordFailure(AppUser user){int count=(user.getFailedLoginCount()==null?0:user.getFailedLoginCount())+1;user.setFailedLoginCount(count);if(count>=MAX_FAILURES){user.setFailedLoginCount(0);user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));}mapper.updateById(user);}
 private boolean isLocked(AppUser u){return u.getLockedUntil()!=null&&u.getLockedUntil().isAfter(LocalDateTime.now());}
 private long enabledAdminCount(){return mapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getRole,"ADMIN").eq(AppUser::getEnabled,true));}
 private String normalizeUsername(String value){String result=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(!result.matches("[a-z0-9][a-z0-9._-]{2,63}"))throw new BusinessException(400,"用户名需为 3 至 64 位小写字母、数字或 ._- ");return result;}
 private String normalizeDisplayName(String value){String result=value==null?"":value.trim();if(result.isEmpty()||result.length()>100)throw new BusinessException(400,"显示名称长度无效");return result;}
 private void validatePassword(String value){if(value==null||value.length()<12||value.getBytes(StandardCharsets.UTF_8).length>72)throw new BusinessException(400,"密码长度需至少 12 个字符且不超过 72 字节");}
}
