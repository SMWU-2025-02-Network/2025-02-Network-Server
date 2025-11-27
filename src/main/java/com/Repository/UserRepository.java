package com.Repository;

import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    User findByUsername(String username);

    //LoginId 칼럼으로 사용자 찾는 함수
    Optional<User> findByLoginId(String loginId);

}