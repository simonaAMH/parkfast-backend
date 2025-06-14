//package com.example.licenta.Repositories;
//
//import com.example.licenta.Models.UserPaymentMethod;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface UserPaymentMethodRepository extends JpaRepository<UserPaymentMethod, String> {
//    List<UserPaymentMethod> findByUserId(String userId);
//    Optional<UserPaymentMethod> findByUserIdAndId(String userId, String id);
//}