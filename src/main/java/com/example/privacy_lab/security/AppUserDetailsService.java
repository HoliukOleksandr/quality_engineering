package com.example.privacy_lab.security;

import com.example.privacy_lab.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email)
                .filter(u -> u.getAnonymizedAt() == null)
                .map(u -> new AppPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}