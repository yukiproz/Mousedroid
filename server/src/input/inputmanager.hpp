#pragma once

#include <string>
#include <iostream>
#include <vector>
#include <sstream>

#ifdef __unix__
    #include <climits>
#endif

#include "logger.h"

namespace InputManager
{
    struct Command 
    {
        char type;
        std::vector<double> args;
    };   

    const char LCLICK = 0x01;
    const char RCLICK = 0x02;
    const char DOWN = 0x03;
    const char UP = 0x04;
    const char MOVE = 0x05;
    const char SCROLL = 0x06;
    const char KEYPRESS = 0x07;
    const char SCROLL_H = 0x08;
    const char ZOOM = 0x09;
    const char MEDIA = 0x0A;
    const char TEXT = 0x0B;

    extern int MOVE_SENSITIVITY;
    extern int SCROLL_SENSITIVITY;
    
    /**
     * @brief Base class to handle all input events.
     * Specialized os classes are dervived from this.
     */
    class Base
    {
        public:
            void execute(const char* bytes, size_t size) const
            {
                switch(bytes[0])
                {
                    case InputManager::MOVE:
                        move(-(int)bytes[1], -(int)bytes[2]);
                        break;
                    
                    case InputManager::LCLICK:
                        click();
                        break;

                    case InputManager::RCLICK:
                        right_click();
                        break;

                    case InputManager::SCROLL:
                        scroll(bytes[1]);
                        break;

                    case InputManager::SCROLL_H:
                        scroll_h(bytes[1]);
                        break;

                    case InputManager::DOWN:
                        down();
                        break;

                    case InputManager::UP:
                        up();
                        break;

                    case InputManager::KEYPRESS:
                        send_key(bytes[1], bytes[2]);
                        break;

                    case InputManager::MEDIA:
                        media(bytes[1]);
                        break;

                    case InputManager::ZOOM:
                        zoom(bytes[1]);
                        break;

                    case InputManager::TEXT: {
                        // Variable-length payload: [TEXT][length_hi][length_lo][UTF-8...]
                        if (size < 3) {
                            break;
                        }
                        size_t length = ((uint8_t)bytes[1] << 8) | (uint8_t)bytes[2];
                        if (length > size - 3) {
                            length = size - 3;
                        }
                        send_text(bytes + 3, length);
                        break;
                    }
                }
            }
            
        private:
            virtual void click() const = 0;
            virtual void right_click() const = 0;
            virtual void move(int dx, int dy) const = 0;
            virtual void scroll(int dy) const = 0;
            virtual void scroll_h(int dx) const = 0;
            virtual void down() const = 0;
            virtual void up() const  = 0;
            virtual void send_key(uint8_t keycode, uint8_t modifier) const = 0;
            virtual void zoom(int scale) const = 0;
            virtual void media(uint8_t action) const = 0;
            virtual void send_text(const char* text, size_t length) const = 0;
    };
}