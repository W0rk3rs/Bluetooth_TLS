#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <errno.h>
#include <string.h>

int main(int argc, char **argv)
{
    struct sockaddr_rc loc_addr = { 0 }, rem_addr = { 0 };
    char buf[1024] = { 0 };
    int s, client, bytes_read;
    socklen_t opt = sizeof(rem_addr);

    // allocate socket
    s = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (s < 0) {
        perror("Failed to create socket");
        return 1;
    }

    // bind socket to port 9 of the first available local bluetooth adapter
    loc_addr.rc_family = AF_BLUETOOTH;
    str2ba("34:CF:F6:86:F6:DB", &loc_addr.rc_bdaddr);
    loc_addr.rc_channel = (uint8_t) 4;
    if (bind(s, (struct sockaddr *)&loc_addr, sizeof(loc_addr)) < 0) {
        perror("Error binding socket");
        close(s);
        return 1;
    }

    // put socket into listening mode
    if (listen(s, 1) < 0) {
        perror("Error listening on socket");
        close(s);
        return 1;
    }
    printf("listening...\n");

    while (1) {
        // accept one connection
        client = accept(s, (struct sockaddr *)&rem_addr, &opt);
        // print the client connection with mac address;
        char client_address_buffer[1024] = { 0 };
        ba2str(&rem_addr.rc_bdaddr, client_address_buffer);
        printf("Connection established with %s\n", client_address_buffer);

        if (client < 0) {
            perror("Error accepting connection");
            close(s);
            return 1;
        }

        ba2str(&rem_addr.rc_bdaddr, buf);
        fprintf(stderr, "accepted connection from %s\n", buf);
        while (1) {
            memset(buf, 0, sizeof(buf));

            // read data from the client
            bytes_read = read(client, buf, sizeof(buf));
            if (bytes_read > 0) {
                printf("received [%s]\n", buf);
                // check if the message is "quit"
                if (strcmp(buf, "quit") == 0) {
                    printf("Received quit command. Closing connection.\n");
                    memset(buf, 0, sizeof(buf));
                    strcpy(buf, "quit");
                    int bytes_sent = send(client, buf, strlen(buf), 0);
                    break;
                }
            } else if (bytes_read < 0) {
                perror("Error reading from client");
            }

            memset(buf, 0, sizeof(buf));
            fgets(buf, sizeof(buf), stdin);
            // strcpy(buf, "HI, THIS IS THE SERVER, HAVE A NICE DAY!!!");
            int bytes_sent = send(client, buf, strlen(buf), 0);
            if (bytes_sent < 0) {
                perror("Error sending to client");
            } else {
                printf("sent [%s]\n", buf);
            }
        }
        printf("Connection with client %s has been closed\n", client_address_buffer);
        close(client);
    }

    // close connection
    close(s);
    return 0;
}
