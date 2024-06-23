#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <signal.h>
#include <openssl/ssl.h>
#include <openssl/err.h>

// Compilation command: gcc -o server server2.c -lssl -lcrypto

#define BUFFER_SIZE 1024

int server_sock;

void handle_sigint(int sig) {
    printf("Shutting down server...\n");
    close(server_sock);
    exit(0);
}

int pem_passwd_cb(char *buf, int size, int rwflag, void *userdata) {
    const char *passphrase = "server";
    strncpy(buf, passphrase, size);
    buf[size - 1] = '\0';  // Ensure null termination
    return strlen(buf);
}

void log_encrypted_data(const char *label, const unsigned char *data, int len) {
    printf("%s:\n", label);
    for (int i = 0; i < len; i++) {
        printf("%02X ", data[i]);
        if ((i + 1) % 16 == 0) printf("\n");
    }
    printf("\n");
}

int main() {
    const char *ip = "127.0.0.1";
    int port = 5555;

    int client_sock = 0;
    struct sockaddr_in server_addr, client_addr;
    socklen_t addr_size;
    char buffer[BUFFER_SIZE] = {0};
    int n = 0;

    // Handle termination signals
    signal(SIGINT, handle_sigint);

    server_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (server_sock < 0) {
        perror("Error in creating socket");
        exit(1);
    }
    
    printf("TCP Server Socket Created\n");

    memset(&server_addr, '\0', sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = inet_addr(ip);

    n = bind(server_sock, (struct sockaddr*)&server_addr, sizeof(server_addr));
    if (n < 0) {
        perror("Error in binding");
        close(server_sock); // Ensure socket is closed on error
        exit(1);
    }

    printf("Bind Successful to port %d\n", port);

    if (listen(server_sock, 5) < 0) {
        perror("Error in listening");
        close(server_sock);
        exit(1);
    }
    printf("Listening...\n");

    // Initialize OpenSSL
    SSL_library_init();
    OpenSSL_add_all_algorithms();
    SSL_load_error_strings();

    while (1) {
        addr_size = sizeof(client_addr);
        client_sock = accept(server_sock, (struct sockaddr*)&client_addr, &addr_size);
        if (client_sock < 0) {
            perror("Error in accepting connection");
            continue;
        }
        printf("Connection Established with client at IP %s and port %d\n", inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port));

        SSL_CTX* ctx = SSL_CTX_new(TLS_server_method());
        if (!ctx) {
            perror("Unable to create SSL context");
            ERR_print_errors_fp(stderr);
            close(client_sock);
            continue;
        }

        // Set the passphrase callback
        SSL_CTX_set_default_passwd_cb(ctx, pem_passwd_cb);

        // Set the cipher list for TLS 1.2 and below
        if (!SSL_CTX_set_cipher_list(ctx, "HIGH:!aNULL:!eNULL:@STRENGTH")) {
            perror("Error setting cipher list");
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }

        // Set the cipher suites for TLS 1.3
        if (!SSL_CTX_set_ciphersuites(ctx, "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256")) {
            perror("Error setting TLS 1.3 ciphersuites");
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }

        // Enforce TLS 1.2 only
        SSL_CTX_set_options(ctx, SSL_OP_NO_TLSv1 | SSL_OP_NO_TLSv1_1 | SSL_OP_NO_TLSv1_3);

        // Load the clients's self-signed certificate
        if (!SSL_CTX_load_verify_locations(ctx, "../keys/client_keys/client.crt", NULL)) {
            perror("Error loading server certificate");
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            exit(1);
        }

        // Set the key and cert
        if (SSL_CTX_use_certificate_file(ctx, "../keys/server_keys/server.crt", SSL_FILETYPE_PEM) <= 0) {
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            exit(1);
        }

        if (SSL_CTX_use_PrivateKey_file(ctx, "../keys/server_keys/server.key", SSL_FILETYPE_PEM) <= 0) {
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            exit(1);
        }

        // Set verification mode
        SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER, NULL);

        // Disable session tickets
        SSL_CTX_set_options(ctx, SSL_OP_NO_TICKET);

        // Create the SSL structure and get the context
        SSL* ssl = SSL_new(ctx);
        if (!ssl) {
            perror("Unable to create SSL structure");
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }
        // Set the file descriptor for the SSL structure
        SSL_set_fd(ssl, client_sock);

        // Perform the SSL handshake
        if (SSL_accept(ssl) <= 0) {
            ERR_print_errors_fp(stderr);
            SSL_free(ssl);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }

        memset(buffer, 0, BUFFER_SIZE);
        n = SSL_read(ssl, buffer, sizeof(buffer) - 1); // Leave space for null terminator

        if (n < 0) {
            perror("Error in receiving data");
        } else {
            buffer[n] = '\0'; // Ensure null termination
            printf("Client: %s\n", buffer);

            snprintf(buffer, BUFFER_SIZE, "HI, THIS IS THE SERVER, HAVE A NICE DAY!!!");

            printf("Server: %s\n", buffer);
            log_encrypted_data("Encrypted message sent to client", (unsigned char *)buffer, strlen(buffer));
            SSL_write(ssl, buffer, strlen(buffer));
        }

        // Clean up SSL
        SSL_shutdown(ssl);
        SSL_free(ssl);
        SSL_CTX_free(ctx);

        close(client_sock);
        printf("Connection with client %s has been closed\n", inet_ntoa(client_addr.sin_addr));
    }

    close(server_sock);
    return 0;
}
