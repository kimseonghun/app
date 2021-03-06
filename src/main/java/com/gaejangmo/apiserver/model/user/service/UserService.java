package com.gaejangmo.apiserver.model.user.service;

import com.gaejangmo.apiserver.model.image.domain.user.service.UserImageService;
import com.gaejangmo.apiserver.model.image.dto.FileResponseDto;
import com.gaejangmo.apiserver.model.image.user.domain.UserImage;
import com.gaejangmo.apiserver.model.like.domain.Likes;
import com.gaejangmo.apiserver.model.like.service.LikeService;
import com.gaejangmo.apiserver.model.user.domain.User;
import com.gaejangmo.apiserver.model.user.domain.UserRepository;
import com.gaejangmo.apiserver.model.user.domain.vo.Motto;
import com.gaejangmo.apiserver.model.user.dto.UserResponseDto;
import com.gaejangmo.apiserver.model.user.dto.UserSearchDto;
import com.gaejangmo.utils.RandomUtils;
import com.gaejangmo.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    private static final int RANDOM_USER_COUNT = 3;
    private static final int USER_START_IDX = 1;
    private static final String USER_NOT_FOUND_MESSAGE = "해당하는 유저가 없습니다.";

    private final UserRepository userRepository;
    private final UserImageService userImageService;
    private final LikeService likeService;

    public UserService(final UserRepository userRepository, final UserImageService userImageService, final LikeService likeService) {
        this.userRepository = userRepository;
        this.userImageService = userImageService;
        this.likeService = likeService;
    }

    @Transactional(readOnly = true)
    public User findById(final Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public User findByUsername(final String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    public UserResponseDto findUserResponseDtoByName(final String username, final Long sourceId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND_MESSAGE));
        boolean liked = likeService.isLiked(sourceId, user.getId());
        int totalLie = likeService.countLikeByTarget(user);
        return toDto(user, liked, totalLie);
    }

    @Transactional(readOnly = true)
    public UserResponseDto findUserResponseDtoByOauthId(final Long oauthId) {
        User user = userRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND_MESSAGE));
        return toDto(user);
    }

    public List<UserSearchDto> findUserSearchDtosByUserName(final String username) {
        if (StringUtils.isEmpty(username)) {
            return Collections.emptyList();
        }

        List<User> users = userRepository.findAllByUsernameContainingIgnoreCase(username);
        return users.stream()
                .map(this::toUserSearchDto)
                .collect(Collectors.toList());
    }

    public UserResponseDto updateMotto(final Long id, final Motto motto) {
        return updateTemplate(id, (user) -> toDto(user.updateMotto(motto)));
    }

    public UserResponseDto updateIntroduce(final Long id, final String introduce) {
        return updateTemplate(id, (user) -> toDto(user.updateIntroduce(introduce)));
    }

    private <T> T updateTemplate(final Long id, final Function<User, T> function) {
        User user = findById(id);
        return function.apply(user);
    }

    public FileResponseDto updateUserImage(final MultipartFile multipartFile, final Long id) {
        User user = findById(id);
        UserImage savedUserImage = userImageService.save(multipartFile, user);

        Optional<UserImage> oldUserImage = user.getUserImage();
        user.updateUserImage(savedUserImage);
        oldUserImage.ifPresent(userImageService::delete);

        return FileResponseDto.builder()
                .createdAt(savedUserImage.getCreatedAt())
                .id(savedUserImage.getId())
                .fileFeature(savedUserImage.getFileFeature())
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> findUserResponseDtoBySourceId(final Long sourceId) {
        return likeService.findAllBySource(sourceId).stream()
                .map(Likes::getTarget)
                .map(user -> toDto(user, true))
                .collect(Collectors.toList());
    }

    public List<UserSearchDto> findRandomUserResponse(final Long sourceId) {
        return RandomUtils.getRandomLongsInRange(USER_START_IDX, userRepository.getMaxId(), RANDOM_USER_COUNT).stream()
                .map(this::findById)
                .map(target -> toUserSearchDto(sourceId, target))
                .collect(Collectors.toList());
    }

    private UserSearchDto toUserSearchDto(final User user) {
        return UserSearchDto.builder()
                .id(user.getId())
                .imageUrl(user.getImageUrl())
                .username(user.getUsername())
                .motto(user.getMotto())
                .isCelebrity(user.isCelebrity())
                .isLiked(false)
                .build();
    }

    private UserSearchDto toUserSearchDto(final Long sourceId, final User user) {
        Long targetId = user.getId();
        boolean isLiked = likeService.isLiked(sourceId, targetId);

        return UserSearchDto.builder()
                .id(user.getId())
                .imageUrl(user.getImageUrl())
                .username(user.getUsername())
                .motto(user.getMotto())
                .isCelebrity(user.isCelebrity())
                .isLiked(isLiked)
                .build();
    }

    /* 사용자한테 전달될 때 null로 넣으면 serialize할 때 제외되기 때문에 null로 지정 */
    private UserResponseDto toDto(final User user) {
        return toDto(user, null, null);
    }

    private UserResponseDto toDto(final User user, final Boolean isLiked) {
        return toDto(user, isLiked, null);
    }

    private UserResponseDto toDto(final User user, final Boolean isLiked, final Integer totalLike) {
        return UserResponseDto.builder()
                .id(user.getId())
                .oauthId(user.getOauthId())
                .username(user.getUsername())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .introduce(user.getIntroduce())
                .motto(user.getMotto())
                .isCelebrity(user.isCelebrity())
                .isLiked(isLiked)
                .totalLike(totalLike)
                .build();
    }
}
