#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <errno.h>

#define BUFFER_SIZE 1024

void log_encrypted_data(const char *label, const unsigned char *data, int len) {
    printf("%s:\n", label);
    for (int i = 0; i < len; i++) {
        printf("%02X ", data[i]);
        if ((i + 1) % 16 == 0) printf("\n");
    }
    printf("\n");
}

int pem_passwd_cb(char *buf, int size, int rwflag, void *userdata) {
    const char *passphrase = "client";
    strncpy(buf, passphrase, size);
    buf[size - 1] = '\0';  // Ensure null termination
    return strlen(buf);
}

int main() {
    int sock = 0;
    struct sockaddr_rc addr = { 0 };
    char buffer[BUFFER_SIZE] = {0};
    char dest[18] = "B0:DC:EF:85:C7:6B";
    int n = 0;

    sock = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (sock < 0) {
        perror("Error in creating socket");
        exit(1);
    }

    // Initialize OpenSSL
    SSL_library_init();
    OpenSSL_add_all_algorithms();
    SSL_load_error_strings();
    
    printf("TCP Client Socket Created\n");

    memset(&addr, '\0', sizeof(addr));
    addr.rc_family = AF_BLUETOOTH;
    addr.rc_channel = (uint8_t) 1;
    str2ba( dest, &addr.rc_bdaddr );

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr))) {
        perror("Error in connecting to server");
        close(sock);
        exit(1);
    }

    printf("Connected to server at MAC address %s and channel %d\n", dest, addr.rc_channel);
    
    memset(buffer, 0, BUFFER_SIZE);

    SSL_CTX* ctx = SSL_CTX_new(TLS_client_method());
    if (!ctx) {
        perror("Unable to create SSL context");
        ERR_print_errors_fp(stderr);
        close(sock);
        exit(1);
    }

    // Set the password callback
    SSL_CTX_set_default_passwd_cb(ctx, pem_passwd_cb);

    // Load the server's self-signed certificate
    if (!SSL_CTX_load_verify_locations(ctx, "../../keys/server_keys/server.crt", NULL)) {
        perror("Error loading server certificate");
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    // Set the key and cert
    if (SSL_CTX_use_certificate_file(ctx, "../../keys/client_keys/client.crt", SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
    }

    if (SSL_CTX_use_PrivateKey_file(ctx, "../../keys/client_keys/client.key", SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
    }

    // Set verification mode
    SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER, NULL);

    // Set the cipher list for TLS 1.2 and below
    if (!SSL_CTX_set_cipher_list(ctx, "HIGH:!aNULL:!eNULL:@STRENGTH")) {
        perror("Error setting cipher list");
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    // Set the cipher suites for TLS 1.3
    if (!SSL_CTX_set_ciphersuites(ctx, "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256")) {
        perror("Error setting TLS 1.3 ciphersuites");
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    // Create the SSL structure and get the context
    SSL* ssl = SSL_new(ctx);
    if (!ssl) {
        perror("Unable to create SSL structure");
        ERR_print_errors_fp(stderr);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    // Set the file descriptor for the SSL structure
    SSL_set_fd(ssl, sock);

    // Perform the SSL handshake
    if (SSL_connect(ssl) <= 0) {
        ERR_print_errors_fp(stderr);
        SSL_free(ssl);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    memset(buffer, 0, BUFFER_SIZE);
    snprintf(buffer, BUFFER_SIZE, "HELLO, THIS IS THE CLIENT...");
    printf("Client: %s\n", buffer);
    log_encrypted_data("Encrypted message sent to server", (unsigned char *)buffer, strlen(buffer));
    if (SSL_write(ssl, buffer, strlen(buffer)) <= 0) {
        ERR_print_errors_fp(stderr);
        SSL_shutdown(ssl);
        SSL_free(ssl);
        SSL_CTX_free(ctx);
        close(sock);
        exit(1);
    }

    memset(buffer, 0, BUFFER_SIZE);
    n = SSL_read(ssl, buffer, sizeof(buffer) - 1);
    if (n < 0) {
        perror("Error in receiving data");
    } else {
        buffer[n] = '\0';
        printf("Server: %s\n", buffer);
    }

    // Clean up and release resources
    SSL_shutdown(ssl);
    SSL_free(ssl);
    SSL_CTX_free(ctx);

    close(sock);
    printf("Disconnected from the server.\n");
    
    return 0;
}
