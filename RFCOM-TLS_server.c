#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <signal.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <errno.h>

// Compilation command: gcc -o server server2.c -lssl -lcrypto

#define BUFFER_SIZE 1024

int server_sock;

void ssl_info_callback(const SSL *ssl, int where, int ret) {
    const char *str;
    int w = where & ~SSL_ST_MASK;

    if (w & SSL_ST_CONNECT) {
        str = "SSL_connect";
    } else if (w & SSL_ST_ACCEPT) {
        str = "SSL_accept";
    } else {
        str = "undefined";
    }

    if (where & SSL_CB_LOOP) {
        printf("%s:%s\n", str, SSL_state_string_long(ssl));
    } else if (where & SSL_CB_ALERT) {
        str = (where & SSL_CB_READ) ? "read" : "write";
        printf("SSL3 alert %s:%s:%s\n", str,
               SSL_alert_type_string_long(ret),
               SSL_alert_desc_string_long(ret));
    } else if (where & SSL_CB_EXIT) {
        if (ret == 0) {
            printf("%s:failed in %s\n", str, SSL_state_string_long(ssl));
        } else if (ret < 0) {
            printf("%s:error in %s\n", str, SSL_state_string_long(ssl));
        }
    }
}

void printBuffer(const unsigned char *buffer, size_t size) {
    // Iterate through each byte in the buffer
    for (size_t i = 0; i < size; ++i) {
        printf("%02X ", buffer[i]); // Print each byte as a two-digit hexadecimal number
        if ((i + 1) % 16 == 0)      // Print a newline after every 16 bytes
            printf("\n");
    }
    printf("\n");
}

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

    struct sockaddr_rc server_addr = { 0 }, client_addr = { 0 };
    char buffer[BUFFER_SIZE] = {0};
    int client_sock = 0;
    socklen_t addr_size = sizeof(client_addr);
    int n = 0;

    // Handle termination signals
    signal(SIGINT, handle_sigint);

    server_sock = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (server_sock < 0) {
        perror("Error in creating socket");
        exit(1);
    }
    
    printf("RFCOM Server Socket Created\n");

    memset(&server_addr, '\0', sizeof(server_addr));
    server_addr.rc_family = AF_BLUETOOTH;
    server_addr.rc_channel = (uint8_t) 4;
    str2ba("34:CF:F6:86:F6:DB", &server_addr.rc_bdaddr);

    n = bind(server_sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0;
    if (n < 0) {
        perror("Error in binding");
        exit(1);
    }

    printf("Bind Successful to port %d\n", server_addr.rc_channel);

    if (listen(server_sock, 5) < 0) {
        perror("Error in listening");
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
        char client_address_buffer[18];
        ba2str(&client_addr.rc_bdaddr, client_address_buffer);
        printf("Connection Established with client at MAC %s and channel %d\n", client_address_buffer, client_addr.rc_channel);

        SSL_CTX* ctx = SSL_CTX_new(TLS_server_method());
        if (!ctx) {
            perror("Unable to create SSL context");
            ERR_print_errors_fp(stderr);
            close(client_sock);
            continue;
        }

        // Set the passphrase callback
        SSL_CTX_set_default_passwd_cb(ctx, pem_passwd_cb);

        SSL_CTX_set_info_callback(ctx, ssl_info_callback);

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

        // Load the server's self-signed certificate
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
            continue;
        }

        if (SSL_CTX_use_PrivateKey_file(ctx, "../keys/server_keys/server.key", SSL_FILETYPE_PEM) <= 0) {
            ERR_print_errors_fp(stderr);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }

        // Set verification mode
        // SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER, NULL);
        SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT, NULL);

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

        printf("Reached this point\n");

        // Perform the SSL handshake
        if (SSL_accept(ssl) <= 0) {
            ERR_print_errors_fp(stderr);
            SSL_free(ssl);
            SSL_CTX_free(ctx);
            close(client_sock);
            continue;
        }

        printf("Performing SSL handshake\n");

        while (1) {
            memset(buffer, 0, BUFFER_SIZE);

            // read data from the client
            n = SSL_read(ssl, buffer, sizeof(buffer) - 1);
            if (n > 0) {
                buffer[n] = '\0';  // Null-terminate the received data
                printf("size of received packets: %d\n", n);
                printf("Buffer byte contents:\n");
                printBuffer((unsigned char *)buffer, n);  // Print only received data
                printf("Client: %s\n", buffer);

                // check if the message is "quit"
                if (strcmp(buffer, "quit") == 0) {
                    printf("Received quit command. Closing connection.\n");
                    memset(buffer, 0, sizeof(buffer));
                    strcpy(buffer, "quit");
                    int bytes_sent = SSL_write(ssl, buffer, strlen(buffer));
                    break;
                }
            } else if (n <= 0) {
                perror("Error reading from client");
            }

            memset(buffer, 0, BUFFER_SIZE);
            fgets(buffer, BUFFER_SIZE, stdin);
            // snprintf(buffer, BUFFER_SIZE, "HI, THIS IS THE SERVER, HAVE A NICE DAY!!!");
            printf("Server: %s\n", buffer);
            log_encrypted_data("Encrypted message sent to client", (unsigned char *)buffer, strlen(buffer));
            SSL_write(ssl, buffer, strlen(buffer));
        }
        printf("Connection with client %s has been closed\n", client_address_buffer);
        printf("Shutting down SSL connection\n");
        // Shutdown the SSL connection
        // Clean up SSL
        SSL_shutdown(ssl);
        SSL_free(ssl);
        SSL_CTX_free(ctx);
        close(client_sock);
    }

    close(server_sock);
    return 0;
}