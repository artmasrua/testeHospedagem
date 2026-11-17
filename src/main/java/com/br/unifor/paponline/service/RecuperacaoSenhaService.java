package com.br.unifor.paponline.service;

import com.br.unifor.paponline.entidades.TokenRecuperacaoSenha;
import com.br.unifor.paponline.entidades.Usuario;
import com.br.unifor.paponline.repository.TokenRecuperacaoSenhaRepository;
import com.br.unifor.paponline.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;

    public String solicitarCodigo(String identificador) {
        usuarioRepository.findByEmailOrTelefone(identificador, identificador)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Não encontramos uma conta com este e-mail/telefone"));

        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        TokenRecuperacaoSenha token = new TokenRecuperacaoSenha();
        token.setIdentificador(identificador);
        token.setCodigo(codigo);
        token.setExpiraEm(LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(token);

        // TODO em produção: enviar por e-mail/SMS
        System.out.println("[RECUPERAÇÃO] Código para " + identificador + ": " + codigo);

        return codigo;
    }

    public void validarCodigo(String identificador, String codigo) {
        TokenRecuperacaoSenha token = tokenRepository
                .findFirstByIdentificadorAndCodigoAndUsadoFalseOrderByCriadoEmDesc(identificador, codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido"));

        if (token.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código expirado. Solicite um novo.");
        }
    }

    public void redefinir(String identificador, String codigo, String novaSenha) {
        validarCodigo(identificador, codigo);

        Usuario usuario = usuarioRepository.findByEmailOrTelefone(identificador, identificador)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        usuario.setSenha(novaSenha);
        usuarioRepository.save(usuario);

        // Marca o token como usado
        tokenRepository
                .findFirstByIdentificadorAndCodigoAndUsadoFalseOrderByCriadoEmDesc(identificador, codigo)
                .ifPresent(t -> {
                    t.setUsado(true);
                    tokenRepository.save(t);
                });
    }
}